(ns iapetos.collector
  (:require [iapetos.metric :as metric])
  (:import [io.prometheus.client
            Collector$MetricFamilySamples
            CollectorRegistry
            SimpleCollector
            SimpleCollector$Builder]))

;; ## Protocol

(defprotocol Collector
  "Protocol for Collectors to be registered with a iapetos registry."
  (instantiate [this registry-options]
    "Return a collector instance that can be registered with collector
     registries.")
  (metric [this]
    "Return a `iapetos.metric/Metric` for this collector.")
  (metric-id [this]
    "Return user supplied (unsanitized) identifier for this collector.")
  (label-instance [this instance values]
    "Add labels to the given collector instance produced by `instantiate`."))

;; ## Labels

(defn- label-array
  ^"[Ljava.lang.String;"
  [labels]
  (into-array String (map metric/sanitize labels)))

(defn- label-names
  [labels]
  (map metric/dasherize labels))

(defn- dasherized-values-map
  "Return values map with keys dasherized."
  [dasherize values]
  (persistent!
   (reduce-kv (fn [m k v]
                (assoc! m (dasherize k) v))
              (transient {})
              values)))

(defn- instance-labeler
  "Return labeler fn that will attach labels to the given
  `SimpleCollector` instance in the correct order while attempting to
  resolve the label value as efficiently as possible. The labeler will
  attempt to resolve the label value in this order:

    1. SimpleCollectorImpl ctor label appears in `values`
    2. SimpleCollectorImpl dasherized label appears in `values`
    3. compute dasherized observed label lookup from `values` and resolve"
  [ctor-labels]
  (let [^objects instance-labels (make-array String (count ctor-labels))
        ctor-labels (vec ctor-labels)
        dasherize (memoize metric/dasherize)]
    (fn set-labels
      [^SimpleCollector instance labels values]
      (let [dasherized-values (delay (dasherized-values-map dasherize values))
            labels (vec labels)]
        (.labels instance
                 (amap instance-labels idx _ret
                       (str (or (get values (nth ctor-labels idx))
                                (get values (nth labels idx))
                                (get @dasherized-values (nth labels idx))))))))))

;; ## Record

(defn- check-subsystem
  [{subsystem :subsystem} {subsystem' :subsystem}]
  (when (and subsystem subsystem' (not= subsystem subsystem'))
    (throw
      (IllegalArgumentException.
        (format
          "collector subsystem (%s) is conflicting with registry subsystem (%s)."
          (pr-str subsystem')
          (pr-str subsystem)))))
  (or subsystem subsystem'))

(defrecord SimpleCollectorImpl [type
                                namespace
                                name
                                metric-id
                                description
                                subsystem
                                labels
                                set-labels
                                builder-constructor
                                lazy?]
  Collector
  (instantiate [this registry-options]
    (let [subsystem (check-subsystem this registry-options)]
      (-> ^SimpleCollector$Builder
          (builder-constructor)
          (.name name)
          (.namespace namespace)
          (.help description)
          (.labelNames (label-array labels))
          (cond-> subsystem (.subsystem subsystem))
          (.create))))
  (metric [_]
    {:name      name
     :namespace namespace})
  (metric-id [_]
    metric-id)
  (label-instance [_ instance values]
    (set-labels instance labels values)))

(defn make-simple-collector
  "Create a new simple collector representation to be instantiated and
   registered with a iapetos registry."
  [{:keys [^String name
           ^String namespace
           ^String subsystem
           ^String description
           labels
           lazy?]
    ::metric/keys [id]}
   collector-type
   builder-constructor]
  {:pre [type name namespace description]}
  (map->SimpleCollectorImpl
    {:type                collector-type
     :namespace           namespace
     :name                name
     :metric-id           id
     :description         description
     :subsystem           subsystem
     :labels              (label-names labels)
     :set-labels          (instance-labeler labels)
     :builder-constructor builder-constructor
     :lazy?               lazy?}))

;; ## Implementation for Raw Collectors

(defn- raw-metric
  [^io.prometheus.client.Collector v]
  (if-let [n (some-> (.collect v)
                     ^Collector$MetricFamilySamples (first)
                     (.name))]
    (let [[a b] (.split n "_" 2)]
      (if b
        {:name b, :namespace a}
        {:name a, :namespace "raw"}))
    {:name      (str (.getSimpleName (class v)) "_" (hash v))
     :namespace "raw"}))

(extend-protocol Collector
  io.prometheus.client.Collector
  (instantiate [this _]
    this)
  (metric [this]
    (raw-metric this))
  (metric-id [this]
    (hash this))
  (label-instance [_ instance values]
    (if-not (empty? values)
      (throw (UnsupportedOperationException.))
      instance)))

;; ## Named Collector

(defn named
  [metric collector]
  (reify Collector
    (instantiate [_ options]
      (instantiate collector options))
    (metric [_]
      metric)
    (metric-id [_]
      metric)
    (label-instance [_ instance values]
      (label-instance collector instance values))))
