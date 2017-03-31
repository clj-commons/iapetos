(ns iapetos.collector.ring-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [iapetos.collector.ring :as ring]))

;; ## Generators

(def gen-handler
  (gen/one-of
    [(gen/let [status (gen/elements
                        (concat
                          (range 200 205)
                          (range 300 308)
                          (range 400 429)
                          (range 500 505)))
               extra  gen/string-alpha-numeric]
              (gen/return
                {:handler (constantly {:status status :iapetos/labels {:extraRes extra}})
                 :exception? false
                 :labels {:status      (str status)
                          :statusClass (str (quot status 100) "XX")
                          :extraRes    extra}}))
     (gen/return
       {:handler    (fn [_] (throw (Exception.)))
        :exception? true})]))

(def gen-request
  (gen/let [path   (gen/fmap #(str "/" %) gen/string-alpha-numeric)
            method (gen/elements [:get :post :put :delete :patch :options :head])
            extra  gen/string-alpha-numeric]
    (gen/return
      {:request-method method
       :uri            path
       :iapetos/labels {:extraReq extra}
       :labels         {:method   (-> method name .toUpperCase)
                        :path     path
                        :extraReq extra}})))

;; ## Tests

(defspec t-wrap-instrumentation 100
  (prop/for-all
    [registry-fn                         (g/registry-fn #(ring/initialize % {:labels [:extraReq :extraRes]}))
     {:keys [handler exception? labels]} gen-handler
     {labels' :labels, :as request}      gen-request
     wrap (gen/elements [ring/wrap-instrumentation ring/wrap-metrics])]
    (let [registry   (registry-fn)
          handler'   (wrap handler registry)
          start-time (System/nanoTime)
          response   (try
                       (handler' request)
                       (catch Throwable t
                         ::error))
          delta      (/ (- (System/nanoTime) start-time) 1e9)
          labels     (merge labels' labels)
          ex-labels  (assoc labels' :exceptionClass "java.lang.Exception")
          counter    (registry :http/requests-total labels)
          histogram  (registry :http/request-latency-seconds labels)
          ex-counter (registry :http/exceptions-total ex-labels)]
      (if exception?
        (and (= response ::error)
             (= 0.0 (prometheus/value counter))
             (= 0.0 (:count (prometheus/value histogram)))
             (= 1.0 (prometheus/value ex-counter)))
        (and (map? response)
             (= 0.0 (prometheus/value ex-counter))
             (< 0.0 (:sum (prometheus/value histogram)) delta)
             (= 1.0 (prometheus/value counter)))))))

(defspec t-wrap-metrics-expose 10
  (prop/for-all
    [registry-fn (g/registry-fn ring/initialize)
     path        (gen/fmap #(str "/" %) gen/string-alpha-numeric)
     wrap        (gen/elements [ring/wrap-metrics-expose ring/wrap-metrics])]
    (let [registry (registry-fn)
          handler (-> (constantly {:status 200})
                      (wrap registry {:path path}))]
      (and (= {:status 200}
              (handler {:request-method :get,  :uri (str path "__/health")}))
           (= {:status 405}
              (handler {:request-method :post, :uri path}))
           (let [{:keys [status headers body]}
                 (handler {:request-method :get, :uri path})]
             (and (= 200 status)
                  (contains? headers "Content-Type")
                  (re-matches #"text/plain(;.*)?" (headers "Content-Type"))
                  (= (export/text-format registry) body)))))))

(defspec t-wrap-metrics-expose-with-on-request-hook 10
  (prop/for-all
    [registry-fn (g/registry-fn ring/initialize)
     path        (gen/fmap #(str "/" %) gen/string-alpha-numeric)
     wrap        (gen/elements [ring/wrap-metrics-expose ring/wrap-metrics])]
    (let [registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/counter :http/scrape-requests-total)))
          on-request-fn #(prometheus/inc % :http/scrape-requests-total)
          handler (-> (constantly {:status 200})
                      (wrap registry
                            {:path path
                             :on-request on-request-fn}))]
      (and (zero? (prometheus/value (registry :http/scrape-requests-total)))
           (= 200 (:status (handler {:request-method :get, :uri path})))
           (= 1.0 (prometheus/value (registry :http/scrape-requests-total)))))))

(deftest default-label-fn
  (is (= {:a 1 :b 2} (#'ring/default-label-fn {:iapetos/labels {:a 1 :b 3}} {:iapetos/labels {:b 2}})))
  (is (= {} (#'ring/default-label-fn {:iapetos/labels {}} nil)))
  (is (= nil (#'ring/default-label-fn {:iapetos/labels nil} nil)))
  (is (= nil (#'ring/default-label-fn nil nil))))
