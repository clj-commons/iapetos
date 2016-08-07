(ns iapetos.export
  (:require [iapetos.registry :as registry])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter
            PushGateway]
           [io.prometheus.client.exporter.common
            TextFormat]))

;; ## TextFormat (v0.0.4)

(defn text-format
  "Dump the given registry using the Prometheus text format (version 0.0.4)."
  [registry]
  (with-open [out (java.io.StringWriter.)]
    (TextFormat/write004
      out
      (.metricFamilySamples ^CollectorRegistry (registry/raw registry)))
    (str out)))

;; ## Push Gateway

(defn push!
  "Create a Prometheus text format representation of the given registry and
   push it to the given gateway."
  [registry
   {:keys [gateway job grouping-key]
    :or {job          (registry/name registry)
         grouping-key {}}}]
  (doto ^PushGateway (if (instance? PushGateway gateway)
                       gateway
                       (PushGateway. ^String gateway))
    (.pushAdd
      ^CollectorRegistry (registry/raw registry)
      job
      grouping-key)))
