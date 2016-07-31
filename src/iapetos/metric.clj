(ns iapetos.metric
  (:require [clojure.string :as string]))

;; ## Protocol

(defprotocol Metric
  (metric-name [metric]))

;; ## Helper

(defn underscore
  [v]
  (string/replace
    (if (keyword? v)
      (name v)
      (str v))
    #"([^a-zA-Z0-9]|[\-_\.])+"
    "_"))

(defn dasherize
  [v]
  (string/replace
    (if (keyword? v)
      (name v)
      (str v))
    #"([^a-zA-Z0-9]|[\-_\.])+"
    "-"))

;; ## Implementation

(extend-protocol Metric
  clojure.lang.Keyword
  (metric-name [k]
    {:name      (-> k name underscore)
     :namespace (or (some->> k namespace underscore)
                    "default")})

  clojure.lang.IPersistentVector
  (metric-name [[namespace name]]
    {:name      (underscore name)
     :namespace (underscore namespace)})

  clojure.lang.IPersistentMap
  (metric-name [{:keys [name namespace]}]
    {:pre [name]}
    {:name (underscore name)
     :namespace (or (some-> namespace underscore) "default")})

  String
  (metric-name [s]
    {:name      (underscore s)
     :namespace "default"}))

;; ## Derived Function

(defn as-map
  [metric additional-keys]
  (merge
    (metric-name metric)
    additional-keys))
