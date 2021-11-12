(defproject clj-commons/iapetos "0.1.12"
  :description "A Clojure Prometheus Client"
  :url "https://github.com/clj-commons/iapetos"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2019
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [io.prometheus/simpleclient "0.12.0"]
                 [io.prometheus/simpleclient_common "0.12.0"]
                 [io.prometheus/simpleclient_pushgateway "0.12.0"]
                 [io.prometheus/simpleclient_hotspot "0.12.0" :scope "provided"]]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "1.1.0"]
                             [aleph "0.4.6"]]
              :global-vars {*warn-on-reflection* true}}
             :codox
             {:plugins [[lein-codox "0.10.0"]]
              :dependencies [[codox-theme-rdash "0.1.2"]]
              :codox {:project {:name "iapetos"}
                      :metadata {:doc/format :markdown}
                      :themes [:rdash]
                      :source-uri "https://github.com/clj-commons/iapetos/blob/v{version}/{filepath}#L{line}"
                      :namespaces [iapetos.core
                                   iapetos.export
                                   iapetos.standalone
                                   #"^iapetos\.collector\..+"]}}
             :coverage
             {:plugins [[lein-cloverage "1.0.9"]]
              :pedantic? :warn
              :dependencies [[org.clojure/tools.reader "1.3.5"]
                             [riddley "0.2.0"]]}}
  :aliases {"codox" ["with-profile" "+codox" "codox"]
            "codecov" ["with-profile" "+coverage" "cloverage" "--codecov"]}
  :pedantic? :abort)
