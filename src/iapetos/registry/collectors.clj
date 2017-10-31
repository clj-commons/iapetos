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

(defn prepare
  [registry metric collector options]
  (let [path (utils/metric->path metric options)
        instance (collector/instantiate collector options)]
    {:collector collector
     :metric    metric
     :path      path
     :raw       instance
     :instance  (register-collector-delay registry instance)}))

(defn insert
  [collectors {:keys [path] :as collector}]
  (assoc-in collectors path collector))

(defn register-if-not-lazy
  [{:keys [collector instance] :as collector-map}]
  (when-not (:lazy? collector)
    @instance)
  collector-map)

;; ## Read Access

(defn- label-collector
  [labels {:keys [collector instance]}]
  (collector/label-instance collector @instance labels))

(defn by
  [collectors metric labels options]
  (some->> (utils/metric->path metric options)
           (get-in collectors)
           (label-collector labels)))
