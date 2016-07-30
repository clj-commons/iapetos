(ns iapetos.collector.fn
  (:require [iapetos.collector :as collector]
            [iapetos.core :as prometheus]
            [iapetos.metric :as metric])
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
           last-failure?
           attempt-count?
           run-count?]
    :or {duration? true
         last-failure? true
         attempt-count? false
         run-count? true}}]
  (let [labels {:fn fn-name, :result "success"}
        failure-labels (assoc labels :result "failure")]
    (wrap->>
      f
      duration?      (prometheus/with-duration
                       (registry :fn/duration-seconds labels))
      last-failure?  (prometheus/with-failure-timestamp
                       (registry :fn/last-failure-unixtime labels))
      run-count?     (prometheus/with-failure-counter
                       (registry :fn/runs-total failure-labels))
      run-count?     (prometheus/with-success-counter
                       (registry :fn/runs-total labels))
      attempt-count? (prometheus/with-counter
                       (registry :fn/attempts-total labels)))))

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

   - `fn_duration_seconds`
   - `fn_last_failure_unixtime`
   - `fn_attempts_total`
   - `fn_runs_total`
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
           :fn/attempts-total
           {:description "the total number of attempted runs of the observed function."
            :labels [:fn]})
         (prometheus/counter
           :fn/runs-total
           {:description "the total number of finished runs of the observed function."
            :labels [:fn :result]}))
       (reduce prometheus/register registry)))

;; ## Constructor

(defn instrument!*
  [registry fn-name fn-var options]
  [{:pre [(string? fn-name) (var? fn-var)]}]
  (instrument-function! registry fn-name fn-var options)
  registry)

(defn instrument!
  ([registry fn-var]
   (instrument! registry fn-var {}))
  ([registry fn-var
    {:keys [duration?
            last-failure?
            failure-count?
            success-count?
            total-count?]
     :as options}]
   (instrument!* registry (subs (str fn-var) 2) fn-var options)))
