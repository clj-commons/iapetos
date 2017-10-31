(ns iapetos.collector.exceptions
  (:require [iapetos.core :as prometheus]
            [iapetos.collector :as collector]
            [iapetos.operations :as ops]))

;; ## Exception Counter Child
;;
;; This will wrap the original collector, the counter instance to use and
;; desired labels. Eventually, we'll add the 'exceptionClass' label and
;; increment the counter from within 'with-exception'.

(deftype ExceptionCounterChild [collector instance values]
  clojure.lang.IFn
  (invoke [_ exception-class]
    (->> (assoc values :exceptionClass exception-class)
         (collector/label-instance collector instance)))

  ops/ReadableCollector
  (read-value [_]
    (if (contains? values :exceptionClass)
      (ops/read-value (collector/label-instance collector instance values))
      (throw
        (IllegalStateException.
          "cannot read exception counter without 'exceptionClass' label."))))

  ops/IncrementableCollector
  (increment* [_ amount]
    (throw
      (IllegalStateException.
        "exception counters cannot be incremented directly."))))

(alter-meta! #'->ExceptionCounterChild assoc :private true)

;; ## Exception Counter
;;
;; This is a wrapper around a counter that defers labeling of the counter
;; instance so it can be done when 'exceptionClass' is known, i.e. from
;; within 'with-exception'.

(deftype ExceptionCounter [counter]
  collector/Collector
  (instantiate [this registry-options]
    (collector/instantiate counter registry-options))
  (metric [this]
    (collector/metric counter))
  (label-instance [_ instance values]
    (->ExceptionCounterChild counter instance values)))

(alter-meta! #'->ExceptionCounter assoc :private true)

;; ## Constructor

(defn exception-counter
  "Create a new exception counter.

   Note that the label `exceptionClass` will be automatically added."
  [metric
   & [{:keys [description labels]
       :or {description "the number and class of encountered exceptions."}}]]
  (->ExceptionCounter
    (prometheus/counter
      metric
      {:description description
       :labels      (conj (vec labels) :exceptionClass)})))

;; ## Logic

(defn ^:no-doc record-exception!
  [^ExceptionCounterChild child ^Throwable t]
  {:pre [(instance? ExceptionCounterChild child)]}
  (let [exception-class (.getName (class t))
        instance (child exception-class)]
    (prometheus/inc instance)))

(defmacro with-exceptions
  "Use the given [[exception-counter]] to collect any Exceptions thrown within
   the given piece of code.

   ```
   (defonce registry
     (-> (prometheus/collector-registry)
         (prometheus/register
           (exeception-counter :app/exceptions-total))))

   (with-exceptions (registry :app/exceptions-total)
     ...)
   ```

   The exception class will be stored in the counter's `exceptionClass` label."
  [exception-counter & body]
  `(let [c# ~exception-counter]
     (try
       (do ~@body)
       (catch Throwable t#
         (record-exception! c# t#)
         (throw t#)))))
