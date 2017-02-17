(ns iapetos.export-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.export :as export]
            [iapetos.core :as prometheus]
            [aleph.http :as http])
  (:import [io.prometheus.client.exporter.common TextFormat]))

;; ## Helpers

(def ^:private port 65433)
(def ^:private push-gateway (str "0:" port))

(defn- start-server-for-promise
  ^java.io.Closeable
  [promise]
  (http/start-server
    (fn [request]
      (->> (-> request
               (update :body #(some-> % slurp))
               (update :body str))
           (deliver promise))
      {:status 202})
    {:port port}))

(defmacro with-push-gateway
  [& body]
  `(let [p# (promise)
         result# (with-open [s# (start-server-for-promise p#)]
                   (let [f# (future ~@body)
                         v# (deref p# 500 ::timeout)]
                     @f#
                     v#))]
     (when (= result# ::timeout)
       (throw
         (Exception.
           "push gateway did not receive request within 500ms.")))
     result#))

(defn- matches-registry?
  [{:keys [request-method headers body] :as x} registry]
  (and (= :post request-method)
       (= TextFormat/CONTENT_TYPE_004 (get headers "content-type"))
       (= (export/text-format registry) body)))

;; ## Tests

(defspec t-pushable-collector-registry 10
  (prop/for-all
    [collectors   g/collectors
     job          gen/string-alpha-numeric
     grouping-key (gen/map gen/string-alpha-numeric gen/string-alpha-numeric)]
    (let [registry (export/pushable-collector-registry
                     {:job          job
                      :push-gateway push-gateway
                      :grouping-key grouping-key})]
      (matches-registry?
        (with-push-gateway
          (export/push! registry))
        registry))))

(defspec t-push-registry! 10
  (prop/for-all
    [collectors   g/collectors
     job          gen/string-alpha-numeric
     grouping-key (gen/map gen/string-alpha-numeric gen/string-alpha-numeric)
     registry-fn  (g/registry-fn)]
    (let [registry (registry-fn)]
      (matches-registry?
        (with-push-gateway
          (export/push-registry!
            registry
            {:job          job
             :push-gateway push-gateway
             :grouping-key grouping-key}))
        registry))))

(defspec t-with-push 10
  (prop/for-all
    [collectors   g/collectors
     job          gen/string-alpha-numeric
     grouping-key (gen/map gen/string-alpha-numeric gen/string-alpha-numeric)]
    (let [registry (export/pushable-collector-registry
                     {:job          job
                      :push-gateway push-gateway
                      :grouping-key grouping-key})]
      (matches-registry?
        (with-push-gateway
          (export/with-push registry
            (comment 'do-something)))
        registry))))

(defspec t-with-push-gateway 10
  (prop/for-all
    [collectors   g/collectors
     job          gen/string-alpha-numeric
     grouping-key (gen/map gen/string-alpha-numeric gen/string-alpha-numeric)]
    (let [registry-promise (promise)]
      (matches-registry?
        (with-push-gateway
          (export/with-push-gateway
            [registry {:job          job
                       :push-gateway push-gateway
                       :grouping-key grouping-key}]
            (deliver registry-promise registry)))
        @registry-promise))))
