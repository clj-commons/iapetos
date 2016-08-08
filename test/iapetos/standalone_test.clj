(ns iapetos.standalone-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [iapetos.standalone :as standalone]
            [aleph.http :as http]))

(def ^:private port 65432)
(def ^:private path "/prometheus-metrics")
(def ^:private metrics-host (str "http://localhost:" port))
(def ^:private metrics-url (str metrics-host path))

(defn- fetch
  [request-method url]
  (try
    (-> (http/request
          {:method            request-method
           :throw-exceptions? false
           :url               url})
        (deref)
        ((juxt :status (comp slurp :body))))
    (catch Throwable t
      (println "exception when querying server:" t)
      t)))

(defspec t-standalone-server 5
  (prop/for-all
    [registry-fn (g/registry-fn)]
    (let [registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/counter :app/runs-total))
                       (prometheus/inc :app/runs-total))]
      (with-open [_ (standalone/metrics-server
                      registry
                      {:port 65432
                       :path "/prometheus-metrics"})]
        (and (= [200 (export/text-format registry)] (fetch :get metrics-url))
             (= [405 "Method not allowed: POST" (fetch :post metrics-url)])
             (= [404 (str "Not found: " path "/_info")]
                (fetch :get (str metrics-url "/_info")))
             (= [404 "Not found: /"] (fetch :get metrics-host)))))))
