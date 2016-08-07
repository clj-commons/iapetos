(ns iapetos.registry
  (:refer-clojure :exclude [get name])
  (:require [iapetos.metric :as metric]
            [iapetos.collector :as collector])
  (:import [io.prometheus.client CollectorRegistry]))

;; ## Protocol

(defprotocol Registry
  "Protocol for the iapetos collector registry."
  (subsystem [registry subsystem-name]
    "Create a new registry that is bound to the given subsystem.")
  (register [registry metric collector]
    "Add the given `iapetos.collector/Collector` to the registry using the
     given name.")
  (get [registry metric labels]
    "Retrieve the collector instance associated with the given metric,
     setting the given labels.")
  (raw [registry]
    "Retrieve the underlying `CollectorRegistry`.")
  (name [registry]
    "Retrieve the registry name (for exporting)."))

;; ## Implementation

(defn- metric->path
  [metric {:keys [subsystem]}]
  (let [{:keys [name namespace]} (metric/metric-name metric)]
    [namespace subsystem name]))

(defn- join-subsystem
  [old-subsystem subsystem]
  (if (seq old-subsystem)
    (metric/sanitize (str old-subsystem "_" subsystem))
    subsystem))

(deftype IapetosRegistry [registry-name registry options metrics]
  Registry
  (register [_ metric collector]
    (let [instance (collector/instantiate collector registry options)
          path (metric->path metric options)]
      (->> {:collector collector
            :instance  instance}
           (assoc-in metrics path)
           (IapetosRegistry. registry-name registry options))))
  (subsystem [_ subsystem-name]
    (assert (string? subsystem-name))
    (IapetosRegistry.
      registry-name
      registry
      (update options :subsystem join-subsystem subsystem-name)
      {}))
  (get [_ metric labels]
    (let [path (metric->path metric options)]
      (when-let [{:keys [instance collector]} (get-in metrics path)]
        (collector/label-instance
          collector
          @instance
          labels))))
  (raw [_]
    registry)
  (name [_]
    registry-name)

  clojure.lang.IFn
  (invoke [this k]
    (get this k {}))
  (invoke [this k labels]
    (get this k labels))

  clojure.lang.ILookup
  (valAt [this k]
    (get this k {}))
  (valAt [this k default]
    (or (get this k {}) default)))

;; ## Constructor

(defn create
  ([] (create "iapetos_registry"))
  ([registry-name]
   (IapetosRegistry. registry-name (CollectorRegistry.) {} {})))
