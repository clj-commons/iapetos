(ns iapetos.core
  (:refer-clojure :exclude [namespace inc dec set])
  (:import [io.prometheus.client
            CollectorRegistry
            Counter   Counter$Child
            Histogram Histogram$Child Histogram$Timer
            Gauge     Gauge$Child     Gauge$Timer
            SimpleCollector$Builder]))

;; ## Metric Map Coercion

(defprotocol MetricMap
  (to-metric-map [metric]))

(extend-protocol MetricMap
  clojure.lang.IPersistentMap
  (to-metric-map [metric]
    metric)

  clojure.lang.IPersistentVector
  (to-metric-map [[namespace name]]
    {:namespace namespace
     :name      name})

  String
  (to-metric-map [metric]
    {:name metric}))

(defn- ->metric
  [metric-type metric]
  (merge
    {:labels []}
    (to-metric-map metric)
    {:type metric-type}))

;; ## Registry Protocol

(defprotocol ^:private PrometheusRegistry
  (namespace  [registry metrics-namespace])
  (subsystem  [registry metrics-subsystem])
  (gauge*     [registry metric])
  (counter*   [registry metric])
  (histogram* [registry metric])
  (collector* [registry metric]))

;; ## Derived Functions

(defn- collector-with-labels
  [{:keys [labels object]} label-values]
  [{:pre [(or (map? label-values) (nil? label-values))]}]
  (->> labels
       (map (comp str #(get label-values %)))
       (into-array String)
       (.labels @object)))

(defn- maybe-collector
  [registry metric]
  (let [{:keys [type labels] :as metric} (to-metric-map metric)]
    (when-let [collector (collector* registry metric)]
      (let [actual-type (:type collector)]
        (when (= actual-type type)
          (collector-with-labels collector labels))))))

(defn collector
  [registry metric]
  (let [{:keys [type labels] :as metric} (to-metric-map metric)
        collector (collector* registry metric)
        actual-type (:type collector)]
    (assert collector (str "no such collector: " (pr-str metric)))
    (assert (= actual-type type)
            (format "collector type mismatch (%s != %s): %s"
                    actual-type
                    type
                    (pr-str metric)))
    (collector-with-labels collector labels)))

(defn counter
  [registry name
   & [{:keys [namespace
              description
              subsystem
              labels
              lazy?]
       :or {description "a counter"}
       :as options}]]
  (->> (merge
         {:description description}
         options
         (to-metric-map name))
       (counter* registry)))

(defn gauge
  [registry name
   & [{:keys [namespace
              description
              subsystem
              labels
              lazy?]
       :or {description "a gauge"}
       :as options}]]
  (->> (merge
         {:description description}
         options
         (to-metric-map name))
       (gauge* registry)))

(defn histogram
  [registry name
   & [{:keys [namespace
              description
              buckets
              subsystem
              labels
              lazy?]
       :or {description "a histogram"}
       :as options}]]
  (->> (merge
         {:description description}
         options
         (to-metric-map name))
       (histogram* registry)))

;; ## Registry Implementation

;; ### Generic Initialization

(defn- initialize-collector
  [collector-type
   collector-constructor
   {:keys [^CollectorRegistry registry
           ^String metrics-namespace
           ^String metrics-subsystem]}
   {:keys [^String name
           ^String namespace
           ^String description
           labels
           lazy?]
    :or {lazy? false}}]
  (let [namespace (or namespace metrics-namespace)
        labels    (vec labels)
        object (delay
                 (-> ^SimpleCollector$Builder (collector-constructor)
                     (.name name)
                     (.help description)
                     (.labelNames (into-array String labels))
                     (cond->
                       namespace         (.namespace metrics-namespace)
                       metrics-subsystem (.subsystem metrics-subsystem))
                     (.register registry)))]
    (when-not lazy?
      @object)
    {:type        collector-type
     :namespace   namespace
     :name        name
     :description description
     :labels      labels
     :object      object}))

;; ### Collectors

(defn- build-gauge
  [registry options]
  (initialize-collector :gauge #(Gauge/build) registry options))

(defn- build-counter
  [registry options]
  (initialize-collector :counter #(Counter/build) registry options))

(defn- build-histogram
  [registry {:keys [buckets]
             :as options}]
  (let [constructor #(cond-> (Histogram/build)
                       (seq buckets) (.buckets (double-array buckets)))]
    (initialize-collector :histogram constructor registry options)))

;; ### Registry

(defn- register
  [registry {:keys [namespace name] :as metric}]
  (assoc-in registry [:metrics namespace name] metric))

(defrecord IapetosRegistry [registry
                            registry-name
                            metrics
                            metrics-namespace
                            metrics-subsystem]
  PrometheusRegistry
  (namespace [this metrics-namespace]
    (assoc this :metrics-namespace metrics-namespace))
  (subsystem [this metrics-subsystem]
    (assoc this :metrics-subsystem metrics-subsystem))
  (gauge* [this options]
    (->> (build-gauge this options) (register this)))
  (counter* [this options]
    (->> (build-counter this options) (register this)))
  (histogram* [this options]
    (->> (build-histogram this options) (register this)))
  (collector* [this {:keys [name namespace]}]
    (get-in metrics [(or namespace metrics-namespace) name])))

(alter-var-root #'->IapetosRegistry vary-meta assoc :private true)

(defn collector-registry
  [registry-name]
  (->IapetosRegistry
    (CollectorRegistry.)
    registry-name
    {}
    registry-name
    nil))

;; ## Metric Collection

;; ### Counter/Gauge

(defn- inc-gauge
  [registry metric amount]
  (let [metric (->metric :gauge metric)]
    (when-let [^Gauge$Child gauge (maybe-collector registry metric)]
      (.inc gauge (double amount))
      registry)))

(defn inc
  ([registry metric]
   (inc registry metric 1.0))
  ([registry metric amount]
   (or (inc-gauge registry metric amount)
       (let [metric (->metric :counter metric)]
         (-> ^Counter$Child (collector registry metric)
             (.inc (double amount)))))
   registry))

(defn dec
  ([registry metric]
   (dec registry metric 1.0))
  ([registry metric amount]
   (let [metric (->metric :gauge metric)]
     (-> ^Gauge$Child (collector registry metric)
         (.dec (double amount))))
   registry))

(defn set
  [registry metric value]
  (let [metric (->metric :gauge metric)]
    (-> ^Gauge$Child (collector registry metric)
        (.set (double value)))
    registry))

(defn set-to-current-time
  [registry metric]
  (let [metric (->metric :gauge metric)]
    (-> ^Gauge$Child (collector registry metric)
        (.setToCurrentTime))
    registry))

;; ### Histogram

(defn observe
  [registry metric value]
  (let [metric (->metric :histogram metric)]
    (-> ^Histogram$Child (collector registry metric)
        (.observe (double value)))
    registry))

;; ## Metrics for Blocks

;; ### Last Success

(defn ^:no-doc with-success-timestamp*
  [registry metric f]
  (let [result (f)]
    (set-to-current-time registry metric)
    result))

(defmacro with-success-timestamp
  [[registry metric] & body]
  `(with-success-timestamp* ~registry ~metric (fn [] ~@body)))

;; ### Duration

(defn ^:no-doc with-duration*
  [registry metric f]
  (let [metric (->metric :gauge metric)
        ^Gauge$Child gauge (collector registry metric)
        ^Gauge$Timer timer (.startTimer gauge)]
    (try
      (f)
      (finally
        (.setDuration timer)))))

(defmacro with-duration
  [[registry metric] & body]
  `(with-duration* ~registry ~metric (fn [] ~@body)))

;; ### Duration Histogram

(defn ^:no-doc with-duration-histogram*
  [registry metric f]
  (let [metric (->metric :histogram metric)
        ^Histogram$Child histogram (collector registry metric)
        ^Histogram$Timer timer (.startTimer histogram)]
    (try
      (f)
      (finally
        (.observeDuration timer)))))

(defmacro with-duration-histogram
  [[registry metric] & body]
  `(with-duration-histogram* ~registry ~metric (fn [] ~@body)))
