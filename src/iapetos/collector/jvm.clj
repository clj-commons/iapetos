(ns iapetos.collector.jvm
  (:require [iapetos.collector :as collector]
            [iapetos.core :as prometheus])
  (:import [io.prometheus.metrics.instrumentation.jvm
            JvmGarbageCollectorMetrics
            JvmMemoryMetrics
            JvmThreadsMetrics
            ProcessMetrics]))

(defn jvm-collector [metric collector]
  (reify collector/Collector
    (instantiate [_ _]
      collector)
    (metric [_]
      metric)
    (metric-id [_]
      metric)
    (label-instance [_ instance _]
      instance)))

;; ## Collectors

(defn standard
  "A set of standard collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (jvm-collector
    {:namespace "iapetos_internal"
     :name      "jvm_standard"}
   (ProcessMetrics/builder)))

(defn gc
  "A set of GC metric collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (jvm-collector
    {:namespace "iapetos_internal"
     :name      "jvm_gc"}
   (JvmGarbageCollectorMetrics/builder)))

(defn memory-pools
  "A set of memory usage metric collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (jvm-collector
    {:namespace "iapetos_internal"
     :name      "jvm_memory_pools"}
   (JvmMemoryMetrics/builder)))

(defn threads
  "A set of thread usage metric collectors for the JVM.
   Can be attached to a iapetos registry using `iapetos.core/register`."
  []
  (jvm-collector
    {:namespace "iapetos_internal"
     :name      "jvm_threads"}
   (JvmThreadsMetrics/builder)))

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