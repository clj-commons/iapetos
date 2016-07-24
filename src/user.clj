(ns user)

(require '[iapetos.core :as prometheus]
         '[iapetos.export :as export]
         '[iapetos.collector.jvm :as jvm]
         '[iapetos.collector.fn :as fn])

(defonce registry
  (-> (prometheus/collector-registry)
      (prometheus/register
        (prometheus/histogram :app/duration-seconds)
        (prometheus/gauge     :app/last-success-unixtime {:lazy? true})
        (prometheus/gauge     :app/active-users-total    {:lazy? true})
        (prometheus/counter   :app/runs-total))))

(prometheus/inc     (registry :app/runs-total))
(prometheus/observe (registry :app/duration-seconds) 0.7)
(prometheus/set     (registry :app/active-users-total) 22)

(def job-latency-histogram
  (prometheus/histogram
    :app/job-latency-seconds
    {:description "job execution latency by job type"
     :labels [:job-type]
     :buckets [1.0 5.0 7.5 10.0 12.5 15.0]}))

(let [registry (-> (prometheus/collector-registry)
                   (prometheus/register
                     job-latency-histogram
                     (jvm/all)))]

  (prometheus/observe
    (registry :app/job-latency-seconds {:job-type "pull"})
    14.2)

  (prometheus/observe
    (registry :app/job-latency-seconds {:job-type "push"})
    8.7)


  (print (export/text-format registry)))

(comment
  (defn- count-users!
    []
    (Thread/sleep (inc (rand-int 100))))

  (defn run
    []
    (if (< (rand-int 500) 250)
      (throw (Exception.))
      )
    12)

  (def registry
    (-> (prometheus/collector-registry)
        (prometheus/register
          (prometheus/summary :x/y))
        (fn/initialize)
        (fn/instrument #'count-users!)
        (fn/instrument #'run)))

  (try
    (dotimes [n 20]
      (count-users!))
    (dotimes [n 10]
      (try
        (run)
        (catch Throwable _)))
    (catch Throwable _))

  (prometheus/observe registry :x/y 4.0)
  (print (prometheus/value (registry :x/y)))

  (require '[iapetos.export :as export])
  #_(print (export/text-format registry)))
