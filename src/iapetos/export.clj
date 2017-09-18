(ns iapetos.export
  (:require [iapetos.registry :as registry])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter
            PushGateway]
           [io.prometheus.client.exporter.common
            TextFormat]))

;; ## TextFormat (v0.0.4)

(defn write-text-format!
  "Dump the given registry to the given writer using the Prometheus text format
   (version 0.0.4)."
  [^java.io.Writer w registry]
  (TextFormat/write004
    w
    (.metricFamilySamples ^CollectorRegistry (registry/raw registry))))

(defn text-format
  "Dump the given registry using the Prometheus text format (version 0.0.4)."
  [registry]
  (with-open [out (java.io.StringWriter.)]
    (write-text-format! out registry)
    (str out)))

;; ## Push Gateway

;; ### Protocol

(defprotocol ^:private Pushable
  (push! [registry]
    "Push all metrics of the given registry."))

;; ### Implementation

(deftype PushableRegistry [internal-registry job push-gateway grouping-key]
  registry/Registry
  (register [_ metric collector]
    (PushableRegistry.
      (registry/register internal-registry metric collector)
      job
      push-gateway
      grouping-key))
  (subsystem [_ subsystem-name]
    (PushableRegistry.
      (registry/subsystem internal-registry subsystem-name)
      job
      push-gateway
      grouping-key))
  (get [_ metric labels]
    (registry/get internal-registry metric labels))
  (raw [_]
    (registry/raw internal-registry))
  (name [_]
    (registry/name internal-registry))

  clojure.lang.IFn
  (invoke [this k]
    (registry/get internal-registry k {}))
  (invoke [this k labels]
    (registry/get internal-registry k labels))

  Pushable
  (push! [this]
    (.pushAdd
      ^PushGateway       push-gateway
      ^CollectorRegistry (registry/raw this)
      ^String            job
      ^java.util.Map     grouping-key)
    this))

(alter-meta! #'->PushableRegistry assoc :private true)

;; ### Constructor

(defn- as-push-gateway
  ^io.prometheus.client.exporter.PushGateway
  [gateway]
  (if (instance? PushGateway gateway)
    gateway
    (PushGateway. ^String gateway)))

(defn- as-grouping-key
  [grouping-key]
  (->> (for [[k v] grouping-key]
         [(name k) (str v)])
       (into {})))

(defn pushable-collector-registry
  "Create a fresh iapetos collector registry whose metrics can be pushed to the
   specified gateway using [[push!]].

   Alternatively, by supplying `:registry`, an existing one can be wrapped to be
   pushable, e.g. the [[default-registry]]."
  [{:keys [job registry push-gateway grouping-key]}]
  {:pre [(string? job) push-gateway]}
  (->PushableRegistry
    (or registry (registry/create job))
    job
    (as-push-gateway push-gateway)
    (as-grouping-key grouping-key)))

(defn push-registry!
  "Directly push all metrics of the given registry to the given push gateway.
   This can be used if you don't have control over registry creation, otherwise
   [[pushable-collector-registry]] and [[push!]] are recommended."
  [registry {:keys [push-gateway job grouping-key]}]
  {:pre [(string? job) push-gateway]}
  (.pushAdd
    (as-push-gateway push-gateway)
    ^CollectorRegistry (registry/raw registry)
    ^String            job
    ^java.util.Map     (as-grouping-key grouping-key))
  registry)

;; ### Macros

(defmacro with-push
  "Use the given [[pushable-collector-registry]] to push metrics after the given
   block of code has run successfully."
  [registry & body]
  `(let [r# ~registry
         result# (do ~@body)]
     (push! r#)
     result#))

(defmacro with-push-gateway
  "Create a [[pushable-collector-registry]], run the given block of code, then
   push all collected metrics.

   ```
   (with-push-gateway [registry {:job \"my-job\", :push-gateway \"0:8080\"}]
     ...)
   ```"
  [[binding options] & body]
  {:pre [binding options]}
  `(let [r# (pushable-collector-registry ~options)]
     (with-push r#
       (let [~binding r#]
         ~@body))))
