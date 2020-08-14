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
  [labels buckets]
  (prometheus/histogram
    :http/request-latency-seconds
    {:description "the response latency for HTTP requests."
     :labels (concat [:method :status :statusClass :path] labels)
     :buckets buckets}))

(defn- make-count-collector
  [labels]
  (prometheus/counter
    :http/requests-total
    {:description "the total number of HTTP requests processed."
     :labels (concat [:method :status :statusClass :path] labels)}))

(defn- make-exception-collector
  [labels]
  (ex/exception-counter
    :http/exceptions-total
    {:description "the total number of exceptions encountered during HTTP processing."
     :labels (concat [:method :path] labels)}))

(defn initialize
  "Initialize all collectors for Ring handler instrumentation. This includes:

   - `http_request_latency_seconds`
   - `http_requests_total`
   - `http_exceptions_total`

   Additional `:labels` can be given which need to be supplied using a
   `:label-fn` in [[wrap-instrumentation]] or [[wrap-metrics]].  "
  [registry
   & [{:keys [latency-histogram-buckets labels]
       :or {latency-histogram-buckets [0.001 0.005 0.01 0.02 0.05 0.1 0.2 0.3 0.5 0.75 1 5]}}]]
  (prometheus/register
    registry
    (make-latency-collector labels latency-histogram-buckets)
    (make-count-collector labels)
    (make-exception-collector labels)))

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

(defn- exception? [response] (instance? Exception response))

(defn- ensure-response-map
  [response exception-status]
  (cond (nil? response)          {:status 404}
        (exception? response)    {:status exception-status}
        (not (map? response))    {:status 200}
        (not (:status response)) (assoc response :status 200)
        :else response))

(defn- status-class
  [{:keys [status]}]
  (str (quot status 100) "XX"))

(defn- status
  [{:keys [status]}]
  (str status))

(defn- labels-for
  ([options request]
   (labels-for options request nil))
  ([{:keys [label-fn path-fn]} {:keys [request-method] :as request} response]
   (merge {:path   (path-fn request)
           :method (-> request-method name string/upper-case)}
          (label-fn request response))))

(defn- record-metrics!
  [{:keys [registry] :as options} delta request response]
  (let [labels           (merge
                           {:status      (status response)
                            :statusClass (status-class response)}
                           (labels-for options request response))
        delta-in-seconds (/ delta 1e9)]
    (-> registry
        (prometheus/inc     :http/requests-total labels)
        (prometheus/observe :http/request-latency-seconds labels delta-in-seconds))))

(defn- exception-counter-for
  [{:keys [registry] :as options} request]
  (->> (labels-for options request)
       (registry :http/exceptions-total)))

(defn- safe [{:keys [exception-status]} f]
  (if exception-status
    (try (f) (catch Exception e e))
    (f)))

(defn- run-instrumented
  [{:keys [handler exception-status] :as options} request]
  (ex/with-exceptions (exception-counter-for options request)
    (let [start-time (System/nanoTime)
          response   (safe options #(handler request))
          delta      (- (System/nanoTime) start-time)]
      (->> (ensure-response-map response exception-status)
           (record-metrics! options delta request))
      (when (exception? response) (throw response))
      response)))

(defn wrap-instrumentation
  "Wrap the given Ring handler to write metrics to the given registry:

   - `http_requests_total`
   - `http_request_latency_seconds`
   - `http_exceptions_total`

   Note that you have to call [[initialize]] on your registry first, to register
   the necessary collectors.

   Be aware that you should implement `path-fn` (which generates the value for
   the `:path` label) if you have any kind of ID in your URIs – since otherwise
   there will be one timeseries created for each observed ID.

   For additional labels in the metrics use `label-fn`, which takes the request
   as a first argument and the response as the second argument.

   Since collectors, and thus their labels, have to be registered before they
   are ever used, you need to provide the list of `:labels` when calling
   [[initialize]]."
  [handler registry
   & [{:keys [path-fn label-fn]
       :or {path-fn  :uri
            label-fn (constantly {})}
       :as options}]]
  (let [options (assoc options
                       :path-fn  path-fn
                       :label-fn label-fn
                       :registry registry
                       :handler  handler)]
    #(run-instrumented options %)))

;; ### Metrics Endpoint

(defn wrap-metrics-expose
  "Expose Prometheus metrics at the given constant URI using the text format.

   If `:on-request` is given, it will be called with the collector registry
   whenever a request comes in (the result will be ignored). This lets you use
   the Prometheus scraper as a trigger for metrics collection."
  [handler registry
   & [{:keys [path on-request]
       :or {path       "/metrics"
            on-request identity}}]]
  (fn [{:keys [request-method uri] :as request}]
    (if (= uri path)
      (if (= request-method :get)
        (do
          (on-request registry)
          (metrics-response registry))
        {:status 405})
      (handler request))))

;; ### Compound Middleware

(defn wrap-metrics
  "A combination of [[wrap-instrumentation]] and [[wrap-metrics-expose]].

   Note that you have to call [[initialize]] on your registry first, to register
   the necessary collectors.

   Be aware that you should implement `path-fn` (which generates the value for
   the `:path` label) if you have any kind of ID in your URIs – since otherwise
   there will be one timeseries created for each observed ID.

   For additional labels in the metrics use `label-fn`, which takes the request
   as a first argument and the response as the second argument.

   Since collectors, and thus their labels, have to be registered before they
   are ever used, you need to provide the list of `:labels` when calling
   [[initialize]]."
  [handler registry
   & [{:keys [path path-fn on-request label-fn]
       :or {path     "/metrics"
            path-fn  :uri
            label-fn (constantly {})}
       :as options}]]
  (-> handler
      (wrap-instrumentation registry options)
      (wrap-metrics-expose registry options)))
