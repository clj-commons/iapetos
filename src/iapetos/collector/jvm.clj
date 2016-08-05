(ns iapetos.collector.jvm
  (:require [iapetos.collector :as collector]
            [iapetos.core :as prometheus])
  (:import [io.prometheus.client
            Collector
            CollectorRegistry]
           [io.prometheus.client.hotspot
            StandardExports
            MemoryPoolsExports
            GarbageCollectorExports
            ThreadExports]))

;; ## Collectors

(defn standard
  "A set of standard collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_standard"}
    (StandardExports.)))

(defn gc
  "A set of GC metric collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_gc"}
    (GarbageCollectorExports.)))

(defn memory-pools
  "A set of memory usage metric collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_memory_pools"}
    (MemoryPoolsExports.)))

(defn threads
  "A set of thread usage metric collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_threads"}
    (ThreadExports.)))

;; ## Initialize

(defn initialize
  "Attach all available JVM collectors to the given registry."
  [registry]
  (-> registry
      (prometheus/register
        (standard)
        (gc)
        (memory-pools)
        (threads))))
