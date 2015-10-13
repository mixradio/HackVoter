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
			(str 
				(hiccup/html	[:div  {:class "section"}
												[:div
													(when adminview [:p [:select
																								[:option "submission"]
																								[:option "votingallowed"]
																								[:option "completed"]]])
													(when allowvoting [:div {:id "uservotefloater"} "floating vote thing"])
													(when (not showvotes) [:div (get-inline-link "/hacks/new" "Add new hack")])]])
				(if (> (count hacks) 0)
					(hiccup/html	[:div  {:class "section"}
													[:div
														(map (fn [hack]
																			(hiccup/html
																				[:div {:class (str "box" (when (and showvotes (== (:votes hack) winning-vote)) " winner") ) :id (:publicid hack)}
																					[:div {:class "hack"} [:a {:href (:imgurl hack)} [:img {:src (:imgurl hack)}]]
																						(when showvotes [:div {:class "vote"} (str (check-zero (:votes hack)) " vote(s)")])
																						(when allowvoting [:div {:class "voterow" } [:button {:class "button votebtn votebtnup" :onclick (str "vote('" (:publicid hack) "',1)")} "+"] [:button {:class "button votebtn votebtndown" :onclick (str "vote('" (:publicid hack) "',-1)")} "-"] [:div {:class "uservote"}]])]
																					[:div
																						[:h1 (util/escape-html (:title hack))]
																						(when adminview
																							[:div {:class "editlink"}
																								(str (get-inline-link (str "/hacks/" (:editorid hack)) "edit")
																										  " | "
																										 (get-inline-link (str "javascript:confirmdelete('" (util/escape-html (:title hack)) "','/admin/" (env :admin-key) "/delete/" (:editorid hack) "');") "delete"))])
																						[:h3 (str "by " (util/escape-html (:creator hack)))]
																						[:span (util/escape-html (:description hack))]]])) hacks)]])
					(hiccup/html [:div  {:class "section"}
													[:div "No hacks yet!"]])))
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
		(get-page (str 	(when adminview "Admin")
										(when (and (not adminview) (:allowvoting config)) "Voting is underway!")
										(when (and (not adminview) (:showvotes config)) "Voting is over!"))
							(str formatted-hacks formatted-config))))

(defn- value-or-string [value]
	(if (not (nil? value)) value ""))

(defn get-edit-page [hack msg]
	(let [isnew (nil? (:publicid hack))]
		(get-page (if isnew "New Hack - bookmark this page to get back to it!" "Edit Hack")
							(hiccup/html [:div  {:class "section"}
															[:form {:action (str "/hacks/" (:editorid hack)) :method "post"}
																[:div [:label {:for "title" :accesskey "t"} "<u>T</u>itle:"] [:input {:type "text" :name "title" :id "title" :value (value-or-string (:title hack))}]]
																[:div [:label {:for "desc" :accesskey "d"} "<u>D</u>escription:"] [:input {:type "text" :name "desc" :id "desc" :value (value-or-string (:description hack))}]]
																[:div [:label {:for "creator" :accesskey "c"} "<u>C</u>reator:"] [:input {:type "text" :name "creator" :id "creator" :value (value-or-string (:creator hack))}]]
																[:input {:type "submit" :value "Save"}]
																(when (not (nil? msg)) [:div {:class "message"} msg])
															]]))))

(defn get-not-authorised []
	(get-page "Oi!"
						(str (hiccup/html [:p "The admin pages need authorisation"])
								 (get-link "/" "Get outta here"))))

(defn get-error []
	(get-page "Sorry!"
						(str (hiccup/html [:p "Something's gone wrong, please give it another go in a minute!"])
								 (get-link "/" "Let's do that again"))))