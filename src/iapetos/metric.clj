(ns iapetos.metric
  (:require [clojure.string :as string])
  (:import [io.prometheus.client Collector]))

;; ## Protocol

(defprotocol Metric
  (metric-name [metric]))

;; ## Helper

(defn- assert-valid-name
  [s original-value]
  (assert
    (re-matches #"[a-zA-Z_:][a-zA-Z0-9_:]*" s)
    (format "invalid metric name: %s (sanitized: %s)"
            (pr-str original-value)
            (pr-str s)))
  s)

(defn sanitize
  [v]
  (-> ^String (if (keyword? v)
                (name v)
                (str v))
      (Collector/sanitizeMetricName)
      (string/replace #"__+" "_")
      (string/replace #"(^_+|_+$)" "")
      (assert-valid-name v)))

(defn dasherize
  [v]
  (-> (sanitize v)
      (string/replace "_" "-")))

;; ## Implementation

(extend-protocol Metric
  clojure.lang.Keyword
  (metric-name [k]
    {:name      (-> k name sanitize)
     :namespace (or (some->> k namespace sanitize)
                    "default")})

  clojure.lang.IPersistentVector
  (metric-name [[namespace name]]
    {:name      (sanitize name)
     :namespace (sanitize namespace)})

  clojure.lang.IPersistentMap
  (metric-name [{:keys [name namespace]}]
    {:pre [name]}
    {:name (sanitize name)
     :namespace (or (some-> namespace sanitize) "default")})

  String
  (metric-name [s]
    {:name      (sanitize s)
     :namespace "default"}))

;; ## Derived Function

(defn as-map
  [metric additional-keys]
  (merge (metric-name metric)
         additional-keys
         ;; user supplied (unsanitized) identifier e.g., :app/runs-total
         {::id metric}))
