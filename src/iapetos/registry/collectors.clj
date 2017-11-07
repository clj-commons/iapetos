(ns iapetos.registry.collectors
  (:require [iapetos.registry.utils :as utils]
            [iapetos.collector :as collector])
  (:import [io.prometheus.client Collector CollectorRegistry]))

;; ## Init

(defn initialize
  []
  {})

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
         :path       path
         :raw        instance
         :register   (register-collector-delay registry instance)
         :unregister (unregister-collector-delay registry instance)}
        (warn-lazy-deprecation!))))

(defn insert
  [collectors {:keys [path] :as collector}]
  (assoc-in collectors path collector))

(defn delete
  [collectors {:keys [path] :as collector}]
  (update-in collectors
             (butlast path)
             dissoc
             (last path)))

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
  (->> (for [[namespace vs] collectors
             [subsystem vs] vs
             [_ collector] vs]
         collector)
       (reduce unregister collectors)))

;; ## Read Access

(defn- label-collector
  [labels {:keys [collector register]}]
  (collector/label-instance collector @register labels))

(defn lookup
  [collectors metric options]
  (some->> (utils/metric->path metric options)
           (get-in collectors)))

(defn by
  [collectors metric labels options]
  (some->> (lookup collectors metric options)
           (label-collector labels)))
