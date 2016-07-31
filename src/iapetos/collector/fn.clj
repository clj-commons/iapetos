(ns iapetos.collector.fn
  (:require [iapetos.collector :as collector]
            [iapetos.core :as prometheus]
            [iapetos.metric :as metric]
            [iapetos.collector.exceptions :as ex])
  (:import [io.prometheus.client CollectorRegistry]))

;; ## Instrumentation

(defmacro ^:private wrap
  [body f]
  `(let [f# ~f]
      (fn [& args#]
        (->> (apply f# args#) ~body))))

(defmacro ^:private wrap->>
  [v & pairs]
  (->> (partition 2 pairs)
       (mapcat
         (fn [[condition body]]
           (list condition `(wrap ~body))))
       (list* `cond->> v)))

(defn wrap-instrumentation
  "Wrap the given function to write a series of execution metrics to the given
   registry. See [[initialize]]."
  [f registry fn-name
   {:keys [duration?
           exceptions?
           last-failure?
           run-count?]
    :or {duration? true
         exceptions? true
         last-failure? true
         run-count? true}}]
  (let [labels {:fn fn-name, :result "success"}
        failure-labels (assoc labels :result "failure")]
    (wrap->>
      f
      duration?      (prometheus/with-duration
                       (registry :fn/duration-seconds labels))
      exceptions?    (ex/with-exceptions
                       (registry :fn/exceptions-total labels))
      last-failure?  (prometheus/with-failure-timestamp
                       (registry :fn/last-failure-unixtime labels))
      run-count?     (prometheus/with-failure-counter
                       (registry :fn/runs-total failure-labels))
      run-count?     (prometheus/with-success-counter
                       (registry :fn/runs-total labels)))))

(defn- instrument-function!
  [registry fn-name fn-var options]
  (let [f' (-> fn-var
              (alter-meta! update ::original #(or % @fn-var))
              (::original)
              (wrap-instrumentation registry fn-name options))]
    (alter-var-root fn-var (constantly f'))))

;; ## Collectors

(defn initialize
  "Enable function instrumentalization by registering the metric collectors.
   Metrics include:

   - `fn_duration_seconds`: a histogram of execution duration,
   - `fn_last_failure_unixtime`: a gauge with the last failure timestamp,
   - `fn_runs_total`: a counter for fn runs, split by success/failure,
   - `fn_exceptions_total`: a counter for fn exceptions, split by class.
   "
  [registry]
  (->> (vector
         (prometheus/histogram
           :fn/duration-seconds
           {:description "the time elapsed during execution of the observed function."
            :labels [:fn]})
         (prometheus/gauge
           :fn/last-failure-unixtime
           {:description "the UNIX timestamp of the last time the observed function threw an exception."
            :labels [:fn]})
         (prometheus/counter
           :fn/runs-total
           {:description "the total number of finished runs of the observed function."
            :labels [:fn :result]})
         (ex/exception-counter
           :fn/exceptions-total
           {:description "the total number and type of exceptions for the observed function."
            :labels [:fn]}))
       (reduce prometheus/register registry)))

;; ## Constructor

(defn- instrument!*
  [registry fn-name fn-var options]
  {:pre [(string? fn-name) (var? fn-var)]}
  (instrument-function! registry fn-name fn-var options)
  registry)

(defn instrument!
  ([registry fn-var]
   (instrument! registry fn-var {}))
  ([registry fn-var
    {:keys [fn-name
            exceptions?
            duration?
            last-failure?
            run-count?]
     :or {fn-name (subs (str fn-var) 2)}
     :as options}]
   (instrument!* registry fn-name fn-var options)))
