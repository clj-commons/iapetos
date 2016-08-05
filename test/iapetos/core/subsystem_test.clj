(ns iapetos.core.subsystem-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.string :as string]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.export :as export]))

;; ## Generators

(def gen-metric-fn
  (gen/elements
    [prometheus/counter
     prometheus/gauge
     prometheus/histogram
     prometheus/summary]))

;; ## Helpers

(defn- parse-subsystems
  [registry]
  (->> (export/text-format registry)
       (re-seq #"TYPE app_(.+)_runs_total ")
       (keep second)
       (sort)))

;; ## Test

(defspec t-subsystem 25
  (prop/for-all
    [registry-fn (g/registry-fn)
     subsystem->metric-fn (gen/bind
                            (->> (gen/vector g/valid-name)
                                 (gen/not-empty)
                                 (gen/fmap (comp sort distinct)))
                            (fn [subsystems]
                              (gen/fmap
                                #(map vector subsystems %)
                                (gen/vector gen-metric-fn (count subsystems)))))]
    (let [registry (registry-fn)
          subsystems (map first subsystem->metric-fn)]
      (doseq [[subsystem metric-fn] subsystem->metric-fn]
        (-> registry
            (prometheus/subsystem subsystem)
            (prometheus/register
              (metric-fn :app/runs-total))))
      (= subsystems (parse-subsystems registry)))))

(defspec t-explicit-subsystem 25
  (prop/for-all
    [registry-fn    (g/registry-fn)
     subsystem-name g/valid-name
     metric-fn      gen-metric-fn]
    (let [registry (-> (registry-fn)
                       (prometheus/register
                         (metric-fn :app/runs-total {:subsystem subsystem-name})))]
      (= [subsystem-name] (parse-subsystems registry)))))

(defspec t-nested-subsystems 25
  (prop/for-all
    [registry-fn     (g/registry-fn)
     subsystem-names (gen/not-empty (gen/vector g/valid-name))
     metric-fn       gen-metric-fn]
    (let [registry (registry-fn)
          subsystem-registry (-> (reduce prometheus/subsystem registry subsystem-names)
                                 (prometheus/register
                                   (metric-fn :app/runs-total)))
          expected-name (string/join "_" subsystem-names)]
      (= [expected-name] (parse-subsystems registry)))))

(defspec t-subsystem-conflict 10
  (prop/for-all
    [registry-fn    (g/registry-fn)
     subsystem-name g/valid-name
     metric-fn      gen-metric-fn]
    (= (try
         (-> (registry-fn)
             (prometheus/subsystem subsystem-name)
             (prometheus/register
               (metric-fn :app/runs-total {:subsystem (str subsystem-name "x")})))
         (catch IllegalArgumentException _
           ::error))
       ::error)))
