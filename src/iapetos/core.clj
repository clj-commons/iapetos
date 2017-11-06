(ns iapetos.core
  (:require [iapetos.collector :as collector]
            [iapetos.metric :as metric]
            [iapetos.operations :as ops]
            [iapetos.registry :as registry])
  (:refer-clojure :exclude [get inc dec set])
  (:import [io.prometheus.client
            CollectorRegistry
            Counter
            Counter$Child
            Histogram
            Histogram$Child
            Histogram$Timer
            Gauge
            Gauge$Child
            Gauge$Timer
            Summary
            Summary$Builder
            Summary$Child]))

;; ## Registry

(def ^{:added "0.1.7"} default-registry
  registry/default)

(defn collector-registry
  "Create a fresh iapetos collector registry."
  ([] (registry/create))
  ([registry-name] (registry/create registry-name)))

(defn register
  "Register the given collectors."
  [registry & collectors]
  (reduce
    (fn [registry collector]
      (registry/register
        registry
        (collector/metric collector)
        collector))
    registry collectors))

(defn ^{:added "0.1.8"} register-lazy
  "Prepare the given collectors but only actually register them on first use.
   This can be useful for metrics that should only be available conditionally,
   e.g. the failure timestamp for a batch job."
  [registry & collectors]
  (reduce
    (fn [registry collector]
      (registry/register-lazy
        registry
        (collector/metric collector)
        collector))
    registry collectors))

(defn register-as
  "Register the given collector under the given metric name. This is useful
   for plain Prometheus collectors, i.e. those that are not provided by
   iapetos."
  [registry metric collector]
  (registry/register registry metric collector))

(defn ^{:added "0.1.8"} unregister
  "Unregister the given collectors."
  [registry & collector-names]
  (reduce registry/unregister registry collector-names))

(defn ^{:added "0.1.8"} clear
  "Unregister the given collectors."
  [registry]
  (registry/clear registry))

(defn subsystem
  "Create a new registry bound to the given subsystem. The resulting value will
   not have access to any of the original registry's collectors.

   Subsystems can be nested, resulting in joining the subsystem names with
   underscores."
  [registry subsystem-name]
  {:pre [subsystem-name]}
  (registry/subsystem registry (metric/sanitize subsystem-name)))

;; ## Collectors

(defn counter
  "Create a new `Counter` collector:

   - `:description`: a description for the counter,
   - `:labels`: a seq of available labels for the counter.
   "
  [metric
   & [{:keys [description labels]
       :or {description "a counter metric."}
       :as options}]]
  (-> (merge
        {:description description}
        (metric/as-map metric options))
      (collector/make-simple-collector :counter #(Counter/build))))

(defn gauge
  "Create a new `Gauge` collector:

   - `:description`: a description for the gauge,
   - `:labels`: a seq of available labels for the gauge.
   "
  [metric
   & [{:keys [description labels]
       :or {description "a gauge metric."}
       :as options}]]
  (-> (merge
         {:description description}
         (metric/as-map metric options))
       (collector/make-simple-collector :gauge #(Gauge/build))))

(defn histogram
  "Create a new `Histogram` collector:

   - `:description`: a description for the histogram,
   - `:buckets`: a seq of double values describing the histogram buckets,
   - `:labels`: a seq of available labels for the histogram.
   "
  [metric
   & [{:keys [description buckets labels]
       :or {description "a histogram metric."}
       :as options}]]
  (-> (merge
        {:description description}
        (metric/as-map metric options))
      (collector/make-simple-collector
        :histogram
        #(cond-> (Histogram/build)
           (seq buckets) (.buckets (double-array buckets))))))

(defn- add-quantile [^Summary$Builder builder [quantile error]]
  (.quantile builder quantile error))

(defn summary
  "Create a new `Summary` collector:

   - `:description`: a description for the summary,
   - `:quantiles`: a map of double [quantile error] entries
   - `:labels`: a seq of available labels for the summary.
   "
  [metric
   & [{:keys [description quantiles labels]
       :or {description "a summary metric."}
       :as options}]]
  (-> (merge
         {:description description}
         (metric/as-map metric options))
      (collector/make-simple-collector
       :summary
       #(reduce add-quantile (Summary/build) quantiles))))

;; ## Raw Operations

(defmacro ^:private with-metric-exception
  [metric & body]
  `(try
     (do ~@body)
     (catch Exception ex#
       (throw
         (RuntimeException.
           (str "error when updating metric: "
                (pr-str ~metric))
           ex#)))))

(defmacro ^:private ?->
  [value predicate then-branch _ else-branch]
  `(let [v# ~value]
     (if (~predicate v#)
       (-> v# ~then-branch)
       (-> v# ~else-branch))))

(defn- registry?
  [value]
  (instance? iapetos.registry.Registry value))

(defn observe
  "Observe the given amount for the desired metric. This can be either called
   using a registry and metric name or directly on a collector:

   ```
   (-> registry (observe :app/active-users-total 10.0))
   (-> registry :app/active-users-total (observe 10.0))
   ```

   The return value of this operation is either the collector or registry that
   was passed in."
  ([collector amount]
   (ops/observe collector amount)
   collector)
  ([registry metric amount]
   (observe registry metric {} amount))
  ([registry metric labels amount]
   (with-metric-exception metric
     (observe (registry/get registry metric labels) amount))
   registry))

(def
  ^{:arglists '([collector]
                [collector amount]
                [registry metric]
                [registry metric amount]
                [registry metric labels]
                [registry metric labels amount])}
  inc
  "Increment the given metric by the given amount. This can be either called
   using a registry and metric name or directly on a collector:

   ```
   (-> registry (inc :app/active-users-total))
   (-> registry :app/active-users-total (inc))
   ```

   The return value of this operation is either the collector or registry that
   was passed in."
  (fn
    ([collector]
     (ops/increment collector 1.0)
     collector)
    ([a b]
     (?-> a
          registry?  (inc b {} 1.0)
          :else      (ops/increment b))
     a)
    ([registry metric amount-or-labels]
     (?-> amount-or-labels
          map?  (as-> <> (inc registry metric <> 1.0))
          :else (as-> <> (inc registry metric {} <>))))
    ([registry metric labels amount]
     (with-metric-exception metric
       (inc (registry/get registry metric labels) amount))
     registry)))

(def
  ^{:arglists '([collector]
                [collector amount]
                [registry metric]
                [registry metric amount]
                [registry metric labels]
                [registry metric labels amount])}
  dec
  "Decrement the given metric by the given amount. This can be either called
   using a registry and metric name or directly on a collector:

   ```
   (-> registry (inc :app/active-users-total))
   (-> registry :app/active-users-total (inc))
   ```

   The return value of this operation is either the collector or registry that
   was passed in."
  (fn
    ([collector]
     (ops/decrement collector 1.0)
     collector)
    ([a b]
     (?-> a
          registry?  (dec b {} 1.0)
          :else      (ops/decrement b))
     a)
    ([registry metric amount-or-labels]
     (?-> amount-or-labels
          map?  (as-> <> (dec registry metric <> 1.0))
          :else (as-> <> (dec registry metric {} <>))))
    ([registry metric labels amount]
     (with-metric-exception metric
       (dec (registry/get registry metric labels) amount))
     registry)))

(defn set
  "Set the given metric to the given value. This can be either called
   using a registry and metric name or directly on a collector:

   ```
   (-> registry (set :app/active-users-total 10.0))
   (-> registry :app/active-users-total (set 10.0))
   ```

   The return value of this operation is either the collector or registry that
   was passed in."
  ([collector amount]
   (ops/set-value collector amount)
   collector)
  ([registry metric amount]
   (set registry metric {} amount))
  ([registry metric labels amount]
   (with-metric-exception metric
     (set (registry/get registry metric labels) amount))
   registry))

(defn set-to-current-time
  "Set the given metric to the current timestamp. This can be either called
   using a registry and metric name or directly on a collector:

   ```
   (-> registry (set-to-current-time :app/last-success-unixtime))
   (-> registry :app/last-success-unixtime set-to-current-time)
   ```

   The return value of this operation is either the collector or registry that
   was passed in."
  ([collector]
   (ops/set-value-to-current-time collector)
   collector)
  ([registry metric]
   (set-to-current-time registry metric {}))
  ([registry metric labels]
   (with-metric-exception metric
     (set-to-current-time (registry/get registry metric labels)))
   registry))

(defn start-timer
  "Start a timer that, when stopped, will store the duration in the given
   metric. This can be either called using a registry and metric name or a
   collector:

   ```
   (-> registry (start-timer :app/duration-seconds))
   (-> registry :app/duration-seconds (start-timer))
   ```

   The return value will be a _function_ that should be called once the
   operation to time has run."
  ([collector]
   (ops/start-timer collector))
  ([registry metric]
   (start-timer registry metric {}))
  ([registry metric labels]
   (with-metric-exception metric
     (start-timer (registry/get registry metric labels)))))

(defn value
  "Read the current value of a metric. This can be either called using a
   registry and a metric name or directly on a collector:

   ```
   (-> registry (value :app/duration-seconds))
   (-> registry :app/duration-seconds (value))
   ```

   The return value depends on the type of collector."
  ([collector]
   (ops/read-value collector))
  ([registry metric]
   (value registry metric {}))
  ([registry metric labels]
   (with-metric-exception metric
     (value (registry/get registry metric labels)))))

;; ## Compound Operations

;; ### Counters

(defmacro with-counter
  "Wrap the given block to increment the given counter once it is done."
  [collector & body]
  `(let [c# ~collector]
     (try
       (do ~@body)
       (finally
         (inc ~collector)))))

(defmacro with-success-counter
  "Wrap the given block to increment the given counter once it has run
   successfully."
  [collector & body]
  `(let [result# (do ~@body)]
     (inc ~collector)
     result#))

(defmacro with-failure-counter
  "Wrap the given block to increment the given counter if it throws."
  [collector & body]
  `(try
     (do ~@body)
     (catch Throwable t#
       (inc ~collector)
       (throw t#))))

(defmacro with-counters
  "Wrap the given block to increment the given counters:

   - `:total`: incremented when the block is left,
   - `:success`: incremented when the block has executed successfully,
   - `:failure`: incremented when the block has thrown an exception.
   "
  [{:keys [total success failure] :as counters} & body]
  {:pre [(map? counters)]}
  (cond->> `(do ~@body)
    failure (list `with-failure-counter failure)
    success (list `with-success-counter success)
    total   (list `with-counter total)))

(defmacro with-activity-counter
  "Wrap the given block to increment the given collector once it is entered
   and decrement it once execution is done. This needs a [[gauge]] collector
   (since [[counter]] ones cannot be decremented).

   Example: Counting the number of in-flight requests in an HTTP server."
  [collector & body]
  `(let [c# ~collector]
     (inc c#)
     (try
       (do ~@body)
       (finally
         (dec c#)))))

;; ## Timestamps

(defmacro with-timestamp
  "Wrap the given block to store the current timestamp in the given collector
   once execution is done.

   Needs a [[gauge]] collector."
  [collector & body]
  `(try
     (do ~@body)
     (finally
       (set-to-current-time ~collector))))

(defmacro with-success-timestamp
  "Wrap the given block to store the current timestamp in the given collector
   once execution is done successfully.

   Needs a [[gauge]] collector."
  [collector & body]
  `(let [result# (do ~@body)]
     (set-to-current-time ~collector)
     result#))

(defmacro with-failure-timestamp
  "Wrap the given block to store the current timestamp in the given collector
   once execution has failed.

   Needs a [[gauge]] collector."
  [collector & body]
  `(try
     (do ~@body)
     (catch Throwable t#
       (set-to-current-time ~collector)
       (throw t#))))

(defmacro with-timestamps
  "Wrap the given block to set a number of timestamps depending on whether
   execution succeeds or fails:

   `:last-run`: the last time the block was run,
   `:last-success`: the last time the block was run successfully,
   `:last-failure`: the last time execution failed.

   All keys are optional but have to point at a [[gauge]] collector if given."
  [{:keys [last-run last-success last-failure]} & body]
  (cond->> `(do ~@body)
    last-failure (list `with-failure-timestamp last-failure)
    last-success (list `with-success-timestamp last-success)
    last-run     (list `with-timestamp last-run)))

;; ### Durations

(defmacro with-duration
  "Wrap the given block to write its execution time to the given collector.

   Works with [[gauge]], [[histogram]] and [[summary]] collectors."
  [collector & body]
  `(let [stop# (start-timer ~collector)]
     (try
       (do ~@body)
       (finally
         (stop#)))))
