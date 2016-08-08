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

(def ^:private path "/prometheus-metrics")

(defn- fetch
  [{:keys [port]} request-method path]
  (try
    (-> (http/request
          {:method            request-method
           :throw-exceptions? false
           :url               (str "http://localhost:" port path)})
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
      (with-open [server (standalone/metrics-server
                           registry
                           {:port 0
                            :path "/prometheus-metrics"})]
        (and (= [200 (export/text-format registry)]
                (fetch server :get path))
             (= [405 "Method not allowed: POST"]
                (fetch server :post path))
             (= [404 (str "Not found: " path "/_info")]
                (fetch server :get (str path "/_info")))
             (= [404 "Not found: /"]
                (fetch server :get "")))))))
