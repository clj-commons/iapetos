(ns iapetos.export
  (:require [iapetos.registry :as registry])
  (:import [io.prometheus.metrics.exporter.pushgateway Format PushGateway PushGateway$Builder]
           [io.prometheus.metrics.expositionformats PrometheusTextFormatWriter]
           [io.prometheus.metrics.model.registry PrometheusRegistry]
           [java.io ByteArrayOutputStream]))

;; ## TextFormat (v0.0.4)

(defn write-text-format!
  "Dump the given registry to the given writer using the Prometheus text format
   (version 0.0.4)."
  [^ByteArrayOutputStream o registry]
  (let [prom-writer ^PrometheusTextFormatWriter (PrometheusTextFormatWriter/create)]
    (.write prom-writer
            o
            (.scrape ^PrometheusRegistry (registry/raw registry)))))

(defn text-format
  "Dump the given registry using the Prometheus text format (version 0.0.4)."
  [registry]
  (with-open [out (ByteArrayOutputStream.)]
    (write-text-format! out registry)
    (str out)))

;; ## Push Gateway

;; ### Protocol

(defprotocol ^:private Pushable
  (push! [registry]
    "Push all metrics of the given registry."))

;; ### Implementation

(declare call-on-internal)

(deftype PushableRegistry [internal-registry push-gateway]
  registry/Registry
  (register [this metric collector]
    (call-on-internal this registry/register metric collector))
  (register-lazy [this metric collector]
    (call-on-internal this registry/register-lazy metric collector))
  (unregister [this metric]
    (call-on-internal this registry/unregister metric))
  (clear [this]
    (call-on-internal this registry/clear))
  (subsystem [this subsystem-name]
    (call-on-internal this registry/subsystem subsystem-name))
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
    (.pushAdd ^PushGateway push-gateway)
    this))

(alter-meta! #'->PushableRegistry assoc :private true)

(defn- call-on-internal
  [^PushableRegistry r f & args]
  (PushableRegistry.
    (apply f (.-internal-registry r) args)
    (.-push-gateway r)))

;; ### Constructor

(defn- with-grouping-key
  ^PushGateway$Builder [^PushGateway$Builder gateway-builder grouping-key]
  (loop [builder gateway-builder
         gkeys   grouping-key]
    (let [[k v] (first gkeys)]
      (if-not (or (nil? k) (nil? v))
        (recur (.groupingKey builder (name k) (str v)) (rest gkeys))
        builder))))

(defn- as-push-gateway
  ^PushGateway
  [gateway registry job grouping-key]
  (if (instance? PushGateway gateway)
    gateway
    (-> (PushGateway/builder)
        (.address ^String gateway)
        (.format Format/PROMETHEUS_TEXT)
        (.registry ^PrometheusRegistry (registry/raw registry))
        (.job ^String job)
        (with-grouping-key grouping-key)
        (.build))))

(defn pushable-collector-registry
  "Create a fresh iapetos collector registry whose metrics can be pushed to the
   specified gateway using [[push!]].

   Alternatively, by supplying `:registry`, an existing one can be wrapped to be
   pushable, e.g. the [[default-registry]]."
  [{:keys [job registry push-gateway grouping-key]}]
  {:pre [(string? job) push-gateway]}
  (let [reg (or registry (registry/create job))]
    (->PushableRegistry
     reg
     (as-push-gateway push-gateway reg job grouping-key))))

(defn push-registry!
  "Directly push all metrics of the given registry to the given push gateway.
   This can be used if you don't have control over registry creation, otherwise
   [[pushable-collector-registry]] and [[push!]] are recommended."
  [registry {:keys [push-gateway job grouping-key]}]
  {:pre [(string? job) push-gateway]}
  (.pushAdd
   (as-push-gateway push-gateway registry job grouping-key))
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
