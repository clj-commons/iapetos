(ns iapetos.collector.http
  (:require [iapetos.core :as prometheus]
            [iapetos.export :as export]
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

(defn initialize
  [registry
   & [{:keys [latency-histogram-buckets]
       :or {latency-histogram-buckets [0.001 0.005 0.01 0.02 0.05 0.1 0.2 0.3 0.5 0.75 1 5]}}]]
  (prometheus/register
    registry
    (make-latency-collector latency-histogram-buckets)
    (make-count-collector)))

;; ## Response

(defn metrics-response
  [registry]
  {:status 200
   :headers {"content-type" TextFormat/CONTENT_TYPE_004}
   :body    (export/text-format registry)})

;; ## Middlewares

(defn- ensure-response-map
  [success? response]
  (cond (not success?)           {:status 500}
        (nil? response)          {:status 404}
        (not (map? response))    {:status 200}
        (not (:status response)) (assoc response :status 200)
        :else response))

(defn- record-metrics!
  [registry delta {:keys [request-method uri]} {:keys [status]}]
  (let [status-class (str (quot status 100) "XX")
        labels {:method      (-> request-method name string/upper-case)
                :status      (str status)
                :statusClass (str status-class)
                :path        uri}
        delta-in-seconds (/ delta 1e9)]
    (-> registry
        (prometheus/inc     :http/requests-total labels)
        (prometheus/observe :http/request-latency-seconds labels delta-in-seconds))))

(defn- run-instrumented
  [registry handler request]
  (let [start-time (System/nanoTime)
        [success? result] (try
                            [true (handler request)]
                            (catch Throwable t
                              [false t]))
        delta (- (System/nanoTime) start-time)]
    (->> result
         (ensure-response-map success?)
         (record-metrics! registry delta request))
    (if success?
      result
      (throw result))))

(defn wrap-instrumentation
  "Wrap the given Ring handler to write metrics to the given registry:

   - `http_requests_total`
   - `http_request_latency_seconds`

   Note that you have to call [[initialize]] on your registry first, to register
   the necessary collectors."
  [handler registry]
  (fn [request]
    (run-instrumented registry handler request)))

(defn wrap-metrics
  "Expose Prometheus metrics at the given constant URI using the text format."
  [handler registry metrics-uri]
  (fn [{:keys [uri] :as request}]
    (if (= uri metrics-uri)
      (metrics-response registry)
      (handler request))))
