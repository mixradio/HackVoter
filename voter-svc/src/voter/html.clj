(ns voter.html
  (:require [clojure.java.io :as io]
  					[clojure.string :as str]
  					[clojure.string :refer [blank?]]
            [environ.core :refer [env]]
            [clojure.tools.logging :refer [warn error]]
  					[hiccup
  						[core :as hiccup]
  						[util :as util]]))

(defn- get-layout [template title content]
	(let [html (slurp (io/input-stream (io/resource (str template ".html"))))]
		(str/replace (str/replace html "{CONTENT}" content) "{TITLE}" title)))

(defn- get-link [uri text]
	(hiccup/html [:p {:class "clear"} [:a {:href uri :class "button signup"} text]]))

(defn- get-inline-link [uri text]
	(hiccup/html [:a {:href uri} text]))

(defn- get-page [title content]
	(get-layout "mainlayout" (hiccup/html [:p title]) content))

(defn- check-zero [value]
	(if (nil? value) 0 value))

(defn- format-hacklist [hacks config adminview]
	(let [allowvoting (and (:allowvoting config) (not adminview))
				showvotes (or adminview (:showvotes config))
				winning-vote (:votes (first hacks))]
		(try
			(when (> (count hacks) 0)
				(hiccup/html	[:div  {:class "section"}
												[:div
													(when adminview [:p [:select
																								[:option "submission"]
																								[:option "votingallowed"]
																								[:option "completed"]]])
													(map (fn [hack]
																		(hiccup/html
																			[:div {:class (str "box" (when (and showvotes (== (:votes hack) winning-vote)) " winner") ) :id (:publicid hack)}
																				[:div {:class "hack"} [:a {:href (:imgurl hack)} [:img {:src (:imgurl hack)}]]
																					(when showvotes [:div {:class "vote"} (str (check-zero (:votes hack)) " vote(s)")])
																					(when allowvoting [:div {:class "uservote"} "votebtns"])]
																				[:div
																					[:h1 (util/escape-html (:title hack))]
																					(when adminview
																						[:div {:class "editlink"}
																							(str (get-inline-link (str "/hacks/" (:editorid hack)) "edit")
																									  " | "
																									 (get-inline-link (str "javascript:confirmdelete('" (util/escape-html (:title hack)) "','/admin/hacks/delete/" (:editorid hack) "');") "delete"))])
																					[:h3 (str "by " (util/escape-html (:creator hack)))]
																					[:span (util/escape-html (:description hack))]]])) hacks)]]))
			(catch Exception e (error (.printStackTrace e))))))

(defn- format-config [config adminview]
	(hiccup/html [:script {:type "text/javascript"} 
		(str "var currency='" (util/escape-html (:currency config)) "';"
				 "var allocation=parseInt('" (util/escape-html (:allocation config)) "');"
				 "var maxspend=parseInt('" (util/escape-html (:maxspend config)) "');"
				 "var allowvoting=" (and (:allowvoting config) (not adminview)) ";"
				 "var showvotes=" (or (:showvotes config) adminview) ";")]))

(defn format-hacks [hacks config adminview]
	(let [formatted-hacks (format-hacklist hacks config adminview)
				formatted-config (format-config config adminview)]
;		(str (:config data) (:items data)))
		(get-page (str "Hack Voter"
										(when adminview " Admin")
										(when (:allowvoting config) " - voting is underway!")
										(when (:showvotes config) " - voting is over!"))
							(str formatted-hacks formatted-config))))

(defn get-error []
	(get-page "Sorry!"
						(str (hiccup/html [:p "Something's gone wrong, please give it another go in a minute!"])
								 (get-link "/" "Let's do that again"))))