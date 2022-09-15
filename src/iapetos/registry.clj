(ns iapetos.registry
  (:refer-clojure :exclude [get name])
  (:require [iapetos.registry
             [collectors :as collectors]
             [utils :as utils]])
  (:import [io.prometheus.client Collector CollectorRegistry]))

;; ## Protocol

(defprotocol Registry
  "Protocol for the iapetos collector registry."
  (subsystem [registry subsystem-name]
    "Create a new registry that is bound to the given subsystem.")
  (register [registry metric collector]
    "Add the given `iapetos.collector/Collector` to the registry using the
     given name.")
  (register-lazy [registry metric collector]
    "Add the given `iapetos.collector/Collector` to the registry using the
     given name, but only actually register it on first use.")
  (unregister [registry metric]
    "Unregister the collector under the given name from the registry.")
  (clear [registry]
    "Clear the registry, removing all collectors from it.")
  (get [registry metric labels]
    "Retrieve the collector instance associated with the given metric,
     setting the given labels.")
  (raw [registry]
    "Retrieve the underlying `CollectorRegistry`.")
  (name [registry]
    "Retrieve the registry name (for exporting)."))

;; ## Implementation

(declare set-collectors)

(deftype IapetosRegistry [registry-name registry options collectors]
  Registry
  (register [this metric collector]
    (->> (collectors/prepare registry metric collector options)
         (collectors/register collectors)
         (set-collectors this)))
  (register-lazy [this metric collector]
    (->> (collectors/prepare registry metric collector options)
         (collectors/insert collectors)
         (set-collectors this)))
  (unregister [this metric]
    (->> (collectors/lookup collectors metric options)
         (collectors/unregister collectors)
         (set-collectors this)))
  (clear [this]
    (->> (collectors/clear collectors)
         (set-collectors this)))
  (subsystem [_ subsystem-name]
    (assert (string? subsystem-name))
    (IapetosRegistry.
      registry-name
      registry
      (update options :subsystem utils/join-subsystem subsystem-name)
      (collectors/initialize)))
  (get [_ metric labels]
    (collectors/by collectors metric labels options))
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

(defn- set-collectors
  [^IapetosRegistry r collectors]
  (->IapetosRegistry
    (.-registry-name r)
    (.-registry r)
    (.-options r)
    collectors))

;; ## Constructor

(defn create
  ([] (create "iapetos_registry"))
  ([registry-name]
   (create registry-name (CollectorRegistry.)))
  ([registry-name ^CollectorRegistry registry]
   (->> (collectors/initialize)
        (IapetosRegistry. registry-name registry {}))))

(def default
  (create
    "prometheus_default_registry"
    (CollectorRegistry/defaultRegistry)))
