(ns iapetos.collector.jvm
  (:require [iapetos.collector :as collector])
  (:import [io.prometheus.client
            Collector
            CollectorRegistry]
           [io.prometheus.client.hotspot
            StandardExports
            MemoryPoolsExports
            GarbageCollectorExports
            ThreadExports]))

;; ## Exports

(defonce ^:private standard-exports
  (delay (StandardExports.)))

(defonce ^:private gc-exports
  (delay (GarbageCollectorExports.)))

(defonce ^:private thread-exports
  (delay (ThreadExports.)))

(defonce ^:private memory-exports
  (delay (MemoryPoolsExports.)))

;; ## Collectors

(defn standard
  "A set of standard collectors for the JVM."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_standard"}
    (StandardExports.)))

(defn gc
  "A set of GC metric collectors for the JVM."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_gc"}
    (GarbageCollectorExports.)))

(defn memory-pools
  "A set of memory usage metric collectors for the JVM."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_memory_pools"}
    (MemoryPoolsExports.)))

(defn threads
  "A set of thread usage metric collectors for the JVM."
  []
  (collector/named
    {:namespace "iapetos_internal"
     :name      "jvm_threads"}
    (ThreadExports.)))

(defn all
  "Includes all available JVM metric collectors."
  []
  (collector/bundle
    {:namespace "iapetos_internal"
     :name      "jvm_all"}
    [(StandardExports.)
     (MemoryPoolsExports.)
     (GarbageCollectorExports.)
     (ThreadExports.)]))
