(ns iapetos.hotspot
  (:import [io.prometheus.client
            Collector
            CollectorRegistry]
           [io.prometheus.client.hotspot
            StandardExports
            MemoryPoolsExports
            GarbageCollectorExports
            ThreadExports]))

(defn initialize
  "Initialize Collectors for the HotSpot JVM.

   (Note that this requires the `io.promethtues/simpleclient_hotspot` dependency
   to be explicitly included.)"
  [{:keys [^CollectorRegistry registry] :as iapetos-registry}
   & [{:keys [memory? gc? threads?]
       :or {memory?  true
            gc?      true
            threads? true}}]]
  (doseq [^Collector collector (list
                                 (StandardExports.)
                                 (when memory?
                                   (MemoryPoolsExports.))
                                 (when gc?
                                   (GarbageCollectorExports.))
                                 (when threads?
                                   (ThreadExports.)))
          :when collector]
    (.register collector registry))
  iapetos-registry)
