(ns iapetos.registry
  (:refer-clojure :exclude [get name])
  (:require [iapetos.metric :as metric]
            [iapetos.collector :as collector])
  (:import [io.prometheus.client CollectorRegistry]))

;; ## Protocol

(defprotocol ^:private Registry
  "Protocol for the iapetos collector registry."
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

(deftype IapetosRegistry [registry-name registry metrics]
  Registry
  (register [_ metric collector]
    (let [instance (collector/instantiate collector registry)
          {:keys [name namespace]} (metric/metric-name metric)]
      (->> {:collector collector
            :instance  instance}
           (assoc-in metrics [namespace name])
           (IapetosRegistry. registry-name registry))))
  (get [_ metric labels]
    (let [{:keys [name namespace]} (metric/metric-name metric)]
      (when-let [{:keys [instance collector]} (get-in metrics [namespace name])]
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
   (IapetosRegistry. registry-name (CollectorRegistry.) {})))
