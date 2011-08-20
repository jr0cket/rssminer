(ns rssminer.db.feed
  (:use [rssminer.database :only [h2-db-factory]]
        [rssminer.time :only [now-seconds]]
        [rssminer.util :only [ignore-error tracep]]
        [rssminer.db.util :only [h2-query id-k with-h2 h2-insert]]
        [clojure.java.jdbc :only [with-connection with-query-results
                                  insert-record update-values]])
  (:import java.io.StringReader))

(defn fetch-rss-links
  "Returns nil when no more"
  ([] (fetch-rss-links 5))
  ([limit] (h2-query
            ["SELECT id, url, last_md5, check_interval, last_modified
              FROM crawler_links
              WHERE next_check_ts < ?
              ORDER BY next_check_ts LIMIT ?" (now-seconds) limit])))

(defn fetch-tags [user-id feed-id]
  (map :tag (h2-query ["SELECT tag FROM feed_tag
              WHERE user_id = ? AND
              feed_id =?" user-id feed-id])))

(defn fetch-comments [user-id feed-id]
  (h2-query ["SELECT id, content, added_ts FROM comments
              WHERE user_id = ? AND
                    feed_id = ? " user-id feed-id]))

(defn insert-tags [feed-id user-id tags]
  (doseq [t tags]
    (with-h2 (insert-record :feed_tag  {:feed_id feed-id
                                        :user_id user-id
                                        :tag t}))))

(defn save-feeds [subscription feeds user-id]
  (doseq [feed (:entries feeds)]
    (let [feed-id
          (id-k (with-h2
                  (insert-record :feeds
                                 (dissoc (assoc feed
                                           :rss_link_id (:id subscription))
                                         :categories))))]
      (insert-tags feed-id user-id (:categories feed)))))

(defn fetch-feeds
  ([rss-link-id limit offset]
     (h2-query ["SELECT id, author, title, summary, link, published_ts
                 FROM feeds
                 WHERE rss_link_id = ? LIMIT ? OFFSET ?"
                rss-link-id limit offset])))

(defn fetch-feeds-for-user
  ([user-id rss-id]
     (fetch-feeds-for-user user-id rss-id 20 0))
  ([user-id rss-id limit offset]
     (map #(assoc %
             :comments (or (fetch-comments user-id (:id %)) [])
             :tags (or (fetch-tags user-id (:id %)) []))
          (fetch-feeds rss-id limit offset))))

(defn insert-rss-xml [xml]
  (h2-insert :rss_xmls
             {:content (StringReader. xml)
              :length (count xml)}))