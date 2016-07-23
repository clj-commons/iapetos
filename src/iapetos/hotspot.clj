(ns iapetos.hotspot
  (:require [iapetos.collector :as collector])
  (:import [io.prometheus.client
            Collector
            CollectorRegistry]
           [io.prometheus.client.hotspot
            StandardExports
            MemoryPoolsExports
            GarbageCollectorExports
            ThreadExports]))

(defrecord HotspotCollectors [memory? gc? threads?]
  collector/Collector
  (instantiate [_ registry]
    (let [collectors (->> [(StandardExports.)
                           (when memory?  (MemoryPoolsExports.))
                           (when gc?  (GarbageCollectorExports.))
                           (when threads?  (ThreadExports.))]
                          (filter identity))]
      (doseq [^Collector collector collectors]
        (.register collector ^CollectorRegistry registry))
      (delay collectors)))
  (metric [_]
    {:namespace "iapetos_internal"
     :name      "hotspot"})
  (label-instance [_ _ _]
    (throw
      (UnsupportedOperationException.
        "trying to access Hotspot JVM metrics directly."))))

(defn collectors
  [& [{:keys [memory? gc? threads?]
      :or {memory?  true
           gc?      true
           threads? true}}]]
  (map->HotspotCollectors
    {:memory?  memory?
     :gc?      gc?
     :threads? threads?}))
