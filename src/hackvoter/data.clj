(ns hackvoter.data
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :refer [blank?]]
            [environ.core :refer [env]]
            [taoensso.faraday :as far]
            [clj-time.core :as time]
 				  	[clojure.tools.logging :refer [warn error]]))

(def votingstage (atom (keyword (env :voting-stage))))
(def valid-stages #{:submission :votingallowed :completed})

(defn- remove-empty-entries
  [map-entries]
  (into {} (remove #(empty? (str (second %))) map-entries)))

(def client-opts
  (remove-empty-entries
    {:access-key (env :aws-access-key)
     :secret-key (env :aws-secret-key)
     :endpoint (env :dynamo-endpoint)}))

(def hack-table (env :hacks-table))
(def votes-table (env :hack-votes-table))

(defn- table-exists? [name]
  (try
    (let [table (far/describe-table client-opts name)
    			exists (zero? (compare :active (:status table)))]
    	exists)
    (catch Exception _ false)))

(defn- query-table [table query]
  (try
    (far/query client-opts table query)
    (catch Exception _ nil)))

(defn- ensure-hacks-table []
	(far/ensure-table client-opts hack-table [:publicid :s]
  	 {:range-keydef [:editorid :s]
  	 	:throughput {:read (env :readalloc-hack) :write (env :writealloc-hack)} :block? true }))

(defn- ensure-votes-table []
	(far/ensure-table client-opts votes-table [:publicid :s]
  	 {:range-keydef [:userid :s]
  	 	:throughput {:read (env :readalloc-vote) :write (env :writealloc-vote)} :block? true }))

(defn get-user-votes [userid]
	(let [allhacks (far/scan client-opts hack-table)
				uservotes (filter (fn[x] (and (zero? (compare userid (:userid x))) (pos? (:votes x)))) (far/scan client-opts votes-table))]
		(map (fn[hack] 
			(let [id (:publicid hack)
						vote (:votes (first (filter (fn[x] (zero? (compare id (:publicid x)))) uservotes)))]
				{:id id :uservotes (if (nil? vote) 0 vote)})) allhacks)))

(defn get-config-items []
	(let [stage @votingstage]
		{	:currency (env :currency)
			:allocation (env :allocation)
			:maxspend (env :max-spend)
			:votingstage stage
			:configuredvotingstage (keyword (env :voting-stage))
			:allowvoting (== (compare :votingallowed stage) 0)
			:showvotes (== (compare :completed stage) 0) }))

(defn update-voting-stage [newstage]
	(let [keystage (keyword newstage)]
		(if (contains? valid-stages keystage)
			(do (reset! votingstage keystage)
					true)
			false)))
	
(defn sum-all-votes []
	(let [votes (far/scan client-opts votes-table)]
		(apply merge-with + (map (fn[vote] (array-map (keyword (:publicid vote)) (:votes vote))) votes))))

(defn sum-all-votes-json []
	(let [votes (far/scan client-opts votes-table)
				config (get-config-items)
				allowvoting (:allowvoting config)
				showvotes (:showvotes config)]
		(if showvotes
			(map (fn[x] {:id (name (key x)) :votes (val x)}) (sum-all-votes))
			[])))

(defn list-hacks [adminview]
	(let [rawhacks (far/scan client-opts hack-table)
				hacks (if adminview rawhacks (map #(dissoc % :editorid) rawhacks))
				config (get-config-items)
				allowvoting (:allowvoting config)
				showvotes (or adminview (:showvotes config))
				votes (sum-all-votes)]
		(prn (str "list-hacks allowvoting=" allowvoting " showvotes=" showvotes " adminview=" adminview))
		(prn votes)
		(if (and showvotes (not (nil? votes)))
			(sort-by :votes > (map (fn [hack] (let [numvotes ((keyword (:publicid hack)) votes)] (assoc hack :votes (if (nil? numvotes) 0 numvotes)))) hacks))
			(sort-by :title hacks))))

(defn store-hack [hack]
	(let [editorid (:editorid hack)
				publicid (:publicid hack)
				title (:title hack)
				description (:description hack)
				creator (:creator hack)
				imgurl (:imgurl hack)]
		(ensure-hacks-table)
		(prn (str "store-hack editorid=" editorid " publicid=" publicid " title=" title " description=" description " creator=" creator " imgurl=" imgurl))
		(far/put-item client-opts hack-table {:publicid publicid
																	 				:editorid editorid
																	 				:title title
																	 				:description description
																	 				:creator creator
																	 				:imgurl imgurl
																	 				:lastupdate (str (time/now))})))

(defn store-vote [userid publicid votes]
	; validate the allocation in case some smart-ass uses jquery to post bad votes :)
  (warn (str "store-vote userid=" userid " publicid=" publicid " votes=" votes " "))
	  (let [config (get-config-items)
  				allowvoting (:allowvoting config)
  				allocation (:allocation config)
  				maxspend (:maxspend config)
  				existingvotes (get-user-votes userid)]
          (warn (str "store-vote " allowvoting " " allocation " " maxspend " " (apply str existingvotes)))
          (let[
  				uservotesincludingthis (+ votes (reduce + (map (fn[x] (if (zero? (compare publicid (:id x))) 0 (:uservotes x))) existingvotes)))
  				votewithinbudget (and (<= votes maxspend) (<= uservotesincludingthis allocation))
  				oktostore (and allowvoting votewithinbudget)]
  		(warn (str "votewithinbudget=" votewithinbudget " uservotesincludingthis=" uservotesincludingthis))
  		(when oktostore
  			(prn (str "store-vote userid=" userid " publicid=" publicid " votes=" votes))
  			(ensure-votes-table)
  			(far/put-item client-opts votes-table {:userid userid
  																						 :publicid publicid
  																						 :votes votes
  																		 				 :lastupdate (str (time/now))}))
  		(when-not oktostore
  			(prn "store-vote validation failed")))
          ))

; primary access by publicid
(defn get-hack-by-publicid [publicid]
	(first (query-table hack-table {:publicid [:eq publicid]})))

(defn get-hack-by-editorid [editorid]
	(first (filter (fn[x] (zero? (compare editorid (:editorid x)))) (far/scan client-opts hack-table))))

(defn- delete-vote [publicidtogo vote]
	(let [publicid (:publicid vote)
				userid (:userid vote)]
		(prn (str "delete-vote " publicid " " userid))
		(when (zero? (compare publicidtogo publicid))
			(far/delete-item client-opts votes-table {:publicid publicid :userid userid})))
	publicidtogo)

(defn delete-hack-and-votes [editorid]
	(str "delete-hack-and-votes " editorid)
	(let [hack (get-hack-by-editorid editorid)
				publicid (:publicid hack)
				votes (far/scan client-opts votes-table)
				proceed (not (nil? hack))]
		; delete votes first...
		(prn proceed)
		(when proceed
			(doseq [v votes] (delete-vote publicid v))
			(far/delete-item client-opts hack-table {:publicid publicid :editorid editorid}))
		editorid))
