(ns voter.html
  (:require [clojure.string :refer [blank?]]
            [environ.core :refer [env]]
            [voter.data :as data]
            [clojure.tools.logging :refer [warn error]]
  					[hiccup.core :as hiccup]))

(defn format-hacks [data adminview]
	(str (:config data) (:items data)))