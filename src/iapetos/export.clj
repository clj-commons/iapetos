(ns iapetos.export
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter
            PushGateway]
           [io.prometheus.client.exporter.common
            TextFormat]))

;; ## TextFormat (v0.0.4)

(defn text-format
  [{:keys [^CollectorRegistry registry]}]
  (with-open [out (java.io.StringWriter.)]
    (TextFormat/write004
      out
      (.metricFamilySamples registry))
    (str out)))

;; ## Push Gateway

(defn push!
  [{:keys [registry-name registry]}
   {:keys [gateway
           url
           job
           grouping-key]
    :or {job          registry-name
         grouping-key {}}}]
  (doto ^PushGateway (or gateway (PushGateway. url))
    (.pushAdd registry job grouping-key)))
