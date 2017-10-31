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
  (get [registry metric labels]
    "Retrieve the collector instance associated with the given metric,
     setting the given labels.")
  (raw [registry]
    "Retrieve the underlying `CollectorRegistry`.")
  (name [registry]
    "Retrieve the registry name (for exporting)."))

;; ## Implementation

(deftype IapetosRegistry [registry-name registry options collectors]
  Registry
  (register [this metric collector]
    (->> (collectors/prepare registry metric collector options)
         (collectors/register-if-not-lazy)
         (collectors/insert collectors)
         (IapetosRegistry. registry-name registry options)))
  (subsystem [_ subsystem-name]
    (assert (string? subsystem-name))
    (IapetosRegistry.
      registry-name
      registry
      (update options :subsystem utils/join-subsystem subsystem-name)
      {}))
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

;; ## Constructor

(defn create
  ([] (create "iapetos_registry"))
  ([registry-name]
   (IapetosRegistry. registry-name (CollectorRegistry.) {} {})))

(def default
  (IapetosRegistry.
    "prometheus_default_registry"
    (CollectorRegistry/defaultRegistry)
    {}
    {}))
