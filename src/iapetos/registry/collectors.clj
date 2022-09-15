(ns iapetos.registry.collectors
  (:require [iapetos.metric :as metric]
            [iapetos.registry.utils :as utils]
            [iapetos.collector :as collector])
  (:import [io.prometheus.client Collector CollectorRegistry]))

;; ## Init

(defn initialize
  "Initialize map for collectors.

   - ::path-cache meta used to prevent additional metric path computation
  requiring metric name sanitization."
  []
  ^{::path-cache {}} {})

;; ## Management

(defn- register-collector-delay
  [^CollectorRegistry registry ^Collector instance]
  (delay
    (.register registry instance)
    instance))

(defn- unregister-collector-delay
  [^CollectorRegistry registry ^Collector instance]
  (delay
    (.unregister registry instance)
    instance))

(defn- warn-lazy-deprecation!
  [{:keys [collector instance] :as collector-map}]
  (let [lazy? (:lazy? collector)]
    (when (some? lazy?)
      (println "collector option ':lazy?' is deprecated, use 'register-lazy' instead.")
      (println "collector: " (pr-str collector))
      (when-not lazy?
        @instance)))
  collector-map)

(defn prepare
  [registry metric collector options]
  (let [path (utils/metric->path metric options)
        instance (collector/instantiate collector options)]
    (-> {:collector  collector
         :metric     metric
         :cache-key  [(collector/metric-id collector) options]
         :path       path
         :raw        instance
         :register   (register-collector-delay registry instance)
         :unregister (unregister-collector-delay registry instance)}
        (warn-lazy-deprecation!))))

(defn insert
  [collectors {:keys [path cache-key] :as collector}]
  (-> collectors
      (vary-meta update ::path-cache assoc cache-key path)
      (assoc-in path collector)))

(defn delete
  [collectors {:keys [path cache-key] :as _collector}]
  (-> collectors
      (vary-meta update ::path-cache dissoc cache-key)
      (update-in (butlast path)
                 dissoc
                 (last path))))

(defn unregister
  [collectors {:keys [register unregister] :as collector}]
  (when (realized? register)
    @unregister)
  (delete collectors collector))

(defn register
  [collectors {:keys [register] :as collector}]
  @register
  (insert collectors collector))

(defn clear
  [collectors]
  (->> (for [[_namespace vs] collectors
             [_subsystem vs] vs
             [_name collector] vs]
         collector)
       (reduce unregister collectors)))

;; ## Read Access

(defn- label-collector
  [labels {:keys [collector register]}]
  (collector/label-instance collector @register labels))

(defn lookup
  [collectors metric options]
  (some->> (or (get-in (meta collectors)
                       [::path-cache [metric options]])
               (utils/metric->path metric options))
           (get-in collectors)))

(defn by
  [collectors metric labels options]
  (some->> (lookup collectors metric options)
           (label-collector labels)))
