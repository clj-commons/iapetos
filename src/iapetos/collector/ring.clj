(ns iapetos.collector.ring
  (:require [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [iapetos.collector.exceptions :as ex]
            [clojure.string :as string])
  (:import [io.prometheus.client.exporter.common TextFormat]))

;; ## Note
;;
;; This implementation stays purposefully close to the one in
;; 'soundcloud/prometheus-clj' in regard metric naming and histogram bucket
;; selection. prometheus-clj was published under the Apache License 2.0:
;;
;;    Copyright 2014 SoundCloud, Inc.
;;
;;    Licensed under the Apache License, Version 2.0 (the "License"); you may
;;    not use this file except in compliance with the License. You may obtain
;;    a copy of the License at
;;
;;        http://www.apache.org/licenses/LICENSE-2.0
;;
;;    Unless required by applicable law or agreed to in writing, software
;;    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
;;    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
;;    License for the specific language governing permissions and limitations
;;    under the License.

;; ## Initialization

(defn- make-latency-collector
  [buckets]
  (prometheus/histogram
    :http/request-latency-seconds
    {:description "the response latency for HTTP requests."
     :labels [:method :status :statusClass :path]}))

(defn- make-count-collector
  []
  (prometheus/counter
    :http/requests-total
    {:description "the total number of HTTP requests processed."
     :labels [:method :status :statusClass :path]}))

(defn- make-exception-collector
  []
  (ex/exception-counter
    :http/exceptions-total
    {:description "the total number of exceptions encountered during HTTP processing."
     :labels [:method :path]}))

(defn initialize
  "Initialize all collectors for Ring handler instrumentation. This includes:

   - `http_request_latency_seconds`
   - `http_requests_total`
   - `http_exceptions_total`
   "
  [registry
   & [{:keys [latency-histogram-buckets]
       :or {latency-histogram-buckets [0.001 0.005 0.01 0.02 0.05 0.1 0.2 0.3 0.5 0.75 1 5]}}]]
  (prometheus/register
    registry
    (make-latency-collector latency-histogram-buckets)
    (make-count-collector)
    (make-exception-collector)))

;; ## Response

(defn metrics-response
  "Create a Ring response map describing the given collector registry's contents
   using the text format (version 0.0.4)."
  [registry]
  {:status 200
   :headers {"Content-Type" TextFormat/CONTENT_TYPE_004}
   :body    (export/text-format registry)})

;; ## Middlewares

;; ### Latency/Count

(defn- ensure-response-map
  [response]
  (cond (nil? response)          {:status 404}
        (not (map? response))    {:status 200}
        (not (:status response)) (assoc response :status 200)
        :else response))

(defn- status-class
  [{:keys [status]}]
  (str (quot status 100) "XX"))

(defn- status
  [{:keys [status]}]
  (str status))

(defn- record-metrics!
  [registry delta {:keys [request-method ::path]} response]
  (let [labels {:method      (-> request-method name string/upper-case)
                :status      (status response)
                :statusClass (status-class response)
                :path        path}
        delta-in-seconds (/ delta 1e9)]
    (-> registry
        (prometheus/inc     :http/requests-total labels)
        (prometheus/observe :http/request-latency-seconds labels delta-in-seconds))))

(defn- exception-counter-for
  [registry {:keys [request-method ::path]}]
  (let [labels {:method (-> request-method name string/upper-case)
                :path   path}]
    (registry :http/exceptions-total labels)))

(defn- run-instrumented
  [registry handler request]
  (ex/with-exceptions (exception-counter-for registry request)
    (let [start-time (System/nanoTime)
          response (handler request)
          delta (- (System/nanoTime) start-time)]
      (->> (ensure-response-map response)
           (record-metrics! registry delta request))
      response)))

(defn wrap-instrumentation
  "Wrap the given Ring handler to write metrics to the given registry:

   - `http_requests_total`
   - `http_request_latency_seconds`
   - `http_exceptions_total`

   Note that you have to call [[initialize]] on your registry first, to register
   the necessary collectors."
  [handler registry
   & [{:keys [path-fn] :or {path-fn :uri}}]]
  (fn [request]
    (->> (assoc request ::path (path-fn request))
         (run-instrumented registry handler))))

;; ### Metrics Endpoint

(defn wrap-metrics-expose
  "Expose Prometheus metrics at the given constant URI using the text format."
  [handler registry
   & [{:keys [path]
       :or {path "/metrics"}}]]
  (fn [{:keys [request-method uri] :as request}]
    (if (= uri path)
      (if (= request-method :get)
        (metrics-response registry)
        {:status 405})
      (handler request))))

;; ### Compound Middleware

(defn wrap-metrics
  "A combination of [[wrap-instrumentation]] and [[wrap-metrics-expose]]."
  [handler registry
   & [{:keys [path]
       :or {path "/metrics"}
       :as options}]]
  (-> handler
      (wrap-instrumentation registry)
      (wrap-metrics-expose registry options)))
