/**
 *  MessageEntry
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; wo even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.data;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.loklak.geo.LocationSource;
import org.loklak.harvester.SourceType;

import com.fasterxml.jackson.core.JsonGenerator;

public class MessageEntry extends AbstractIndexEntry implements IndexEntry {
    
    protected Date created_at, on, to; // created_at will allways be set, on means 'valid from' and 'to' means 'valid_until' and may not be set
    protected SourceType source_type; // where did the message come from
    protected ProviderType provider_type;  // who created the message
    protected String provider_hash, screen_name, id_str, text;
    protected URL status_id_url;
    protected long retweet_count, favourites_count;
    protected ArrayList<String> texts, images, audio, videos;
    protected String place_name, place_id;
    
    // the following fields are either set as a common field or generated by extraction from field 'text' or from field 'place_name'
    protected double[] location_point, location_mark; // coordinate order is [longitude, latitude]
    protected int location_radius; // meter
    protected LocationSource location_source;

    // the following can be computed from the tweet data but is stored in the search index to provide statistical data and ranking attributes
    private int without_l_len, without_lu_len, without_luh_len; // the length of tweets without links, users, hashtags
    private String[] hosts, links, mentions, hashtags; // the arrays of links, users, hashtags

    public MessageEntry() throws MalformedURLException {
        this.created_at = new Date();
        this.on = null;
        this.to = null;
        this.source_type = SourceType.USER;
        this.provider_type = ProviderType.NOONE;
        this.provider_hash = "";
        this.screen_name = "";
        this.id_str = "";
        this.text = "";
        this.status_id_url = null;
        this.retweet_count = 0;
        this.favourites_count = 0;
        this.texts = new ArrayList<String>();
        this.images = new ArrayList<String>();
        this.audio = new ArrayList<String>();
        this.videos = new ArrayList<String>();
        this.place_id = "";
        this.place_name = "";
        this.location_point = null;
        this.location_radius = 0;
        this.location_mark = null;
        this.location_source = null;
        this.without_l_len = 0;
        this.without_lu_len = 0;
        this.without_luh_len = 0;
        this.hosts = new String[0];
        this.links = new String[0]; 
        this.mentions = new String[0];
        this.hashtags = new String[0];
    }

    public MessageEntry(Map<String, Object> map) {
        Object created_at_obj = map.get("created_at"); this.created_at = parseDate(created_at_obj);
        Object on_obj = map.get("on"); this.on = on_obj == null ? null : parseDate(on);
        Object to_obj = map.get("to"); this.to = to_obj == null ? null : parseDate(to);
        String source_type_string = (String) map.get("source_type");
        if (source_type_string == null) source_type_string = SourceType.USER.name();
        try {
            this.source_type = SourceType.valueOf(source_type_string);
        } catch (IllegalArgumentException e) {
            this.source_type = SourceType.USER;
        }
        String provider_type_string = (String) map.get("provider_type");
        if (provider_type_string == null) provider_type_string = ProviderType.GENERIC.name();
        try {
            this.provider_type = ProviderType.valueOf(provider_type_string);
        } catch (IllegalArgumentException e) {
            this.provider_type = ProviderType.GENERIC;
        }
        this.provider_hash = (String) map.get("provider_hash");
        this.screen_name = (String) map.get("screen_name");
        this.id_str = (String) map.get("id_str");
        this.text = (String) map.get("text");
        try {
            this.status_id_url = new URL((String) map.get("link"));
        } catch (MalformedURLException e) {
            this.status_id_url = null;
        }
        this.retweet_count = parseLong((Number) map.get("retweet_count"));
        this.favourites_count = parseLong((Number) map.get("favourites_count"));
        this.texts = parseArrayList(map.get("texts"));
        this.images = parseArrayList(map.get("images"));
        this.audio = parseArrayList(map.get("audio"));
        this.videos = parseArrayList(map.get("videos"));
        this.place_id = parseString((String) map.get("place_id"));
        this.place_name = parseString((String) map.get("place_name"));
        
        // optional location
        Object location_point_obj = map.get("location_point");
        Object location_radius_obj = map.get("location_radius");
        Object location_mark_obj = map.get("location_mark");
        Object location_source_obj = map.get("location_source");
        if (location_point_obj == null || location_mark_obj == null ||
            !(location_point_obj instanceof List<?>) ||
            !(location_mark_obj instanceof List<?>)) {
            this.location_point = null;
            this.location_radius = 0;
            this.location_mark = null;
            this.location_source = null;
        } else {
            this.location_point = new double[]{(Double) ((List<?>) location_point_obj).get(0), (Double) ((List<?>) location_point_obj).get(1)};
            this.location_radius = (int) parseLong((Number) location_radius_obj);
            this.location_mark = new double[]{(Double) ((List<?>) location_mark_obj).get(0), (Double) ((List<?>) location_mark_obj).get(1)};
            this.location_source = LocationSource.valueOf((String) location_source_obj);;
        }
        
        // load enriched data
        enrich();
    }
    
    public Date getCreatedAt() {
        return this.created_at == null ? new Date() : this.created_at;
    }

    public void setCreatedAt(Date created_at) {
        this.created_at = created_at;
    }

    public Date getOn() {
        return this.on;
    }

    public void setOn(Date on) {
        this.on = on;
    }

    public Date getTo() {
        return this.to;
    }

    public void setTo(Date to) {
        this.to = to;
    }
    
    public SourceType getSourceType() {
        return this.source_type;
    }

    public void setSourceType(SourceType source_type) {
        this.source_type = source_type;
    }

    public ProviderType getProviderType() {
        return provider_type;
    }

    public void setProviderType(ProviderType provider_type) {
        this.provider_type = provider_type;
    }

    public String getProviderHash() {
        return provider_hash;
    }

    public void setProviderHash(String provider_hash) {
        this.provider_hash = provider_hash;
    }

    public String getScreenName() {
        return screen_name;
    }

    public void setScreenName(String user_screen_name) {
        this.screen_name = user_screen_name;
    }

    public String getIdStr() {
        return id_str;
    }

    public void setIdStr(String id_str) {
        this.id_str = id_str;
    }

    public URL getStatusIdUrl() {
        return this.status_id_url;
    }

    public void setStatusIdUrl(URL status_id_url) {
        this.status_id_url = status_id_url;
    }

    public long getRetweetVount() {
        return retweet_count;
    }

    public void setRetweetCount(long retweet_count) {
        this.retweet_count = retweet_count;
    }

    public long getFavouritesCount() {
        return this.favourites_count;
    }

    public void setFavouritesCount(long favourites_count) {
        this.favourites_count = favourites_count;
    }

    public String getPlaceName() {
        return place_name;
    }

    public void setPlaceName(String place_name) {
        this.place_name = place_name;
    }

    public String getPlaceId() {
        return place_id;
    }

    public void setPlaceId(String place_id) {
        this.place_id = place_id;
    }

    public double[] getLocationPoint() {
        return location_point;
    }

    public void setLocationPoint(double[] location_point) {
        this.location_point = location_point;
    }

    public double[] getLocationMark() {
        return location_mark;
    }

    public void setLocationMark(double[] location_mark) {
        this.location_mark = location_mark;
    }

    public int getLocationRadius() {
        return location_radius;
    }

    public void setLocationRadius(int location_radius) {
        this.location_radius = location_radius;
    }

    public LocationSource getLocationSource() {
        return location_source;
    }

    public void setLocationSource(LocationSource location_source) {
        this.location_source = location_source;
    }
    
    public void setText(String text) {
        this.text = text;
    }

    public void setImages(ArrayList<String> images) {
        this.images = images;
    }

    public void setImages(String[] images) {
        this.images = new ArrayList<>();
        for (String image: images) this.images.add(image);
    }

    public void setImages(String image) {
        this.images = new ArrayList<>();
        this.images.add(image);
    }
    
    public long getId() {
        return Long.parseLong(this.id_str);
    }

    public String[] getHosts() {
        return this.hosts;
    }
    
    public String getText() {
        return this.text;
    }

    public String[] getMentions() {
        return this.mentions;
    }

    public String[] getHashtags() {
        return this.hashtags;
    }

    public String[] getLinks() {
        return this.links;
    }

    public ArrayList<String> getImages() {
        return this.images;
    }

    final static Pattern SPACEX_PATTERN = Pattern.compile("  +"); // two or more
    final static Pattern URL_PATTERN = Pattern.compile("(?:\\b|^)(https?://.*?)(?:[) ]|$)"); // right boundary must be space since others may appear in urls
    final static Pattern USER_PATTERN = Pattern.compile("(?:[ (]|^)(@..*?)(?:\\b|$)"); // left boundary must be space since the @ is itself a boundary
    final static Pattern HASHTAG_PATTERN = Pattern.compile("(?:[ (]|^)(#..*?)(?:\\b|$)"); // left boundary must be a space since the # is itself a boundary

    /**
     * create enriched data, useful for analytics and ranking:
     * - identify all mentioned users, hashtags and links
     * - count message size without links
     * - count message size without links and without users
     */
    public void enrich() {
        StringBuilder t = new StringBuilder(this.text);

        // extract the links
        List<String> links = extract(t, URL_PATTERN, 1);
        t = new StringBuilder(SPACEX_PATTERN.matcher(t).replaceAll(" ").trim());
        this.without_l_len = t.length(); // len_no_l
        
        // extract the users
        List<String> users = extract(t, USER_PATTERN, 1);
        t = new StringBuilder(SPACEX_PATTERN.matcher(t).replaceAll(" ").trim());
        this.without_lu_len = t.length(); // len_no_l_and_users
        
        // extract the hashtags
        List<String> hashtags = extract(t, HASHTAG_PATTERN, 1);
        t = new StringBuilder(SPACEX_PATTERN.matcher(t).replaceAll(" ").trim());
        this.without_luh_len = t.length(); // len_no_l_and_users_and_hashtags

        // extract the hosts from the links
        Set<String> hosts = new LinkedHashSet<String>();
        for (String u: links) {
            try {
                URL url = new URL(u);
                hosts.add(url.getHost());
            } catch (MalformedURLException e) {}
        }

        this.hosts = new String[hosts.size()];
        int j = 0; for (String host: hosts) this.hosts[j++] = host.toLowerCase();
        
        this.mentions = new String[users.size()];
        for (int i = 0; i < users.size(); i++) this.mentions[i] = users.get(i).substring(1);
        
        this.hashtags = new String[hashtags.size()];
        for (int i = 0; i < hashtags.size(); i++) this.hashtags[i] = hashtags.get(i).substring(1).toLowerCase();

        this.links = new String[links.size()];
        for (int i = 0; i < links.size(); i++) this.links[i] = links.get(i);
        
        // more media data, analyse the links
        for (String link: this.links) {
            if (link.indexOf("vimeo.com") > 0) {this.videos.add(link); continue;}
            if (link.indexOf("youtube.com") > 0) {this.videos.add(link); continue;}
            if (link.indexOf("soundcloud.com") > 0) {this.audio.add(link); continue;}
        }
    }
    
    private static List<String> extract(StringBuilder s, Pattern p, int g) {
        Matcher m = p.matcher(s.toString());
        List<String> l = new ArrayList<String>();
        while (m.find()) l.add(m.group(g));
        for (String r: l) {int i = s.indexOf(r); s.replace(i, i + r.length(), "");}
        return l;
    }
    
    @Override
    public void toJSON(JsonGenerator m) {
        toJSON(m, null, true); // very important to include calculated data here because that is written into the index using the abstract index factory
    }
    
    public void toJSON(JsonGenerator json, UserEntry user, boolean calculatedData) {
        try {
            json.writeStartObject();

            // tweet data
            writeDate(json, "created_at", getCreatedAt().getTime());
            if (this.on != null) writeDate(json, "on", this.on.getTime());
            if (this.to != null) writeDate(json, "to", this.to.getTime());
            json.writeObjectField("screen_name", this.screen_name);
            json.writeObjectField("text", this.text); // the tweet
            if (this.status_id_url != null) json.writeObjectField("link", this.status_id_url.toExternalForm());
            json.writeObjectField("id_str", this.id_str);
            json.writeObjectField("source_type", this.source_type.name());
            json.writeObjectField("provider_type", this.provider_type.name());
            if (this.provider_hash != null && this.provider_hash.length() > 0) json.writeObjectField("provider_hash", this.provider_hash);
            json.writeObjectField("retweet_count", this.retweet_count);
            json.writeObjectField("favourites_count", this.favourites_count); // there is a slight inconsistency here in the plural naming but thats how it is noted in the twitter api
            writeArray(json, "texts", this.texts);
            json.writeObjectField("texts_count", this.texts.size());
            writeArray(json, "images", this.images);
            json.writeObjectField("images_count", this.images.size());
            writeArray(json, "audio", this.audio);
            json.writeObjectField("audio_count", this.audio.size());
            writeArray(json, "videos", this.videos);
            json.writeObjectField("videos_count", this.videos.size());
            json.writeObjectField("place_name", this.place_name);
            json.writeObjectField("place_id", this.place_id);
  
            // add optional location data. This is written even if calculatedData == false if the source is from REPORT to prevent that it is lost
            if ((calculatedData || this.location_source == LocationSource.REPORT) &&
                this.location_point != null && this.location_mark != null) {
                writeArray(json, "location_point", this.location_point);
                json.writeObjectField("location_radius", this.location_radius);
                writeArray(json, "location_mark", this.location_mark);
                json.writeObjectField("location_source", this.location_source.name());
            }
            
            // add statistic/calculated data
            if (calculatedData) {
                writeArray(json, "hosts", this.hosts);
                json.writeObjectField("hosts_count", this.hosts.length);
                writeArray(json, "links", this.links);
                json.writeObjectField("links_count", this.links.length);
                writeArray(json, "mentions", this.mentions);
                json.writeObjectField("mentions_count", this.mentions.length);
                writeArray(json, "hashtags", this.hashtags);
                json.writeObjectField("hashtags_count", this.hashtags.length);
                // experimental, for ranking
                json.writeObjectField("without_l_len", this.without_l_len);
                json.writeObjectField("without_lu_len", this.without_lu_len);
                json.writeObjectField("without_luh_len", this.without_luh_len);
            }
            
            // add user
            if (user != null) {
                json.writeFieldName("user");
                user.toJSON(json);
            }
            
            //m.field("coordinates", ""); // like {"coordinates":[-75.14310264,40.05701649],"type":"Point"}
            //m.field("entities", ""); // like {"hashtags":[],"urls":[],"user_mentions":[]}
            //m.field("filter_level", "");
            //m.field("user", user.toJSON()); // {"profile_image_url":"...", "name":"Twitter API", "description":"blabla", "location":"San Francisco, CA", "followers_count":665829, "url":"http:\/\/dev.twitter.com", "screen_name":"twitterapi", "id_str":"6253282", "lang":"en", "id":6253282}
            json.writeEndObject();
        } catch (IOException e) {
        }
    }
    
    public static String html2utf8(String s) {
        int p, q;
        // hex coding &#
        while ((p = s.indexOf("&#")) >= 0) {
            q = s.indexOf(';', p + 2);
            if (q < p) break;
            s = s.substring(0, p) + ((char) Integer.parseInt(s.substring(p + 2, q), 16)) + s.substring(q + 1);
        }
        // octal coding \\u
        while ((p = s.indexOf("\\u")) >= 0) {
            char r = ((char) Integer.parseInt(s.substring(p + 2, p + 6), 8));
            if (r < ' ') r = ' ';
            s = s.substring(0, p) + r + s.substring(p + 6);
        }
        // remove tags
        s = s.replaceAll("</a>", "").replaceAll("&quot;", "\"").replaceAll("&amp;", "&");
        // remove funny symbols
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < ' ') s = s.substring(0, i) + ' ' + s.substring(i + 1);
        }
        // remove double spaces
        s = s.replaceAll("  ", " ");
        return s;
    }
}
