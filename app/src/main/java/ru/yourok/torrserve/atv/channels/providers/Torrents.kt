package ru.yourok.torrserve.atv.channels.providers

import com.google.gson.Gson
import ru.yourok.torrserve.num.entity.Entity
import ru.yourok.torrserve.server.api.Api
import ru.yourok.torrserve.utils.ByteFmt

data class Torrent(val name: String,
                   val magnet: String,
                   val hash: String,
                   val length: String,
                   val poster: String,
                   val files: String,
                   val entity: Entity?)

/*
{
	"Name": "Маугли дикой планеты \/ Terra Willy: Planète inconnue \/ Astro Kid (2019) BDRemux 1080p от селезень | iTunes",
	"Magnet": "magnet:?xt=urn:btih:030c1eef674dc5ed38f3b429b1cea1523360f07a&dn=%D0%9C%D0%B0%D1%83%D0%B3%D0%BB%D0%B8+%D0%B4%D0%B8%D0%BA%D0%BE%D0%B9+%D0%BF%D0%BB%D0%B0%D0%BD%D0%B5%D1%82%D1%8B+%2F+Terra+Willy%3A+Plan%C3%A8te+inconnue+%2F+Astro+Kid+%282019%29+BDRemux+1080p+%D0%BE%D1%82+%D1%81%D0%B5%D0%BB%D0%B5%D0%B7%D0%B5%D0%BD%D1%8C+%7C+iTunes&tr=udp%3A%2F%2Fopentor.org%3A2710&tr=udp%3A%2F%2Fopentor.org%3A2710&tr=http%3A%2F%2Fretracker.local%2Fannounce",
	"Hash": "030c1eef674dc5ed38f3b429b1cea1523360f07a",
	"AddTime": 1571135197,
	"Length": 16653284947,
	"Status": 0,
	"Playlist": "\/torrent\/play?link=magnet%3A%3Fxt%3Durn%3Abtih%3A030c1eef674dc5ed38f3b429b1cea1523360f07a%26dn%3D%25D0%259C%25D0%25B0%25D1%2583%25D0%25B3%25D0%25BB%25D0%25B8%2B%25D0%25B4%25D0%25B8%25D0%25BA%25D0%25BE%25D0%25B9%2B%25D0%25BF%25D0%25BB%25D0%25B0%25D0%25BD%25D0%25B5%25D1%2582%25D1%258B%2B%252F%2BTerra%2BWilly%253A%2BPlan%25C3%25A8te%2Binconnue%2B%252F%2BAstro%2BKid%2B%25282019%2529%2BBDRemux%2B1080p%2B%25D0%25BE%25D1%2582%2B%25D1%2581%25D0%25B5%25D0%25BB%25D0%25B5%25D0%25B7%25D0%25B5%25D0%25BD%25D1%258C%2B%257C%2BiTunes%26tr%3Dudp%253A%252F%252Fopentor.org%253A2710%26tr%3Dudp%253A%252F%252Fopentor.org%253A2710%26tr%3Dhttp%253A%252F%252Fretracker.local%252Fannounce&m3u=true&fname=Terra.Willy.Planète.Inconnue.aka.Astro.Kid.2019.BDREMUX.1080p.seleZen.mkv.m3u",
	"Info": "{\"adult\":false,\"backdrop_path\":\"http:\/\/image.tmdb.org\/t\/p\/original\/ivKorzMwX7ZEWtJozRn6Q3VgZgO.jpg\",\"budget\":0,\"genres\":[{\"id\":16,\"name\":\"мультфильм\"},{\"id\":35,\"name\":\"комедия\"},{\"id\":12,\"name\":\"приключения\"},{\"id\":10751,\"name\":\"семейный\"}],\"homepage\":\"\",\"id\":580600,\"imdb_id\":\"tt8329148\",\"media_type\":\"movie\",\"original_language\":\"fr\",\"original_title\":\"Terra Willy, planète inconnue\",\"overview\":\"После крушения космического корабля Вилли попадает на дикую неизученную планету. С помощью робота-спасателя Бака он должен продержаться на новой земле и дождаться спасения. Вилли, Бак и Флэш — инопланентное создание, с которым они подружились — исследуют флору и фауну планеты, а также сталкиваются с опасностями.\",\"popularity\":15.601,\"poster_path\":\"http:\/\/image.tmdb.org\/t\/p\/original\/bkmmvluiT60B9GroNMZrfSYECp2.jpg\",\"release_date\":\"2019-04-03\",\"revenue\":0,\"runtime\":90,\"status\":\"Released\",\"tagline\":\"Приключения космического масштаба\",\"title\":\"Маугли дикой планеты \/ Terra Willy: Planète inconnue \/ Astro Kid (2019) BDRemux 1080p от селезень | iTunes\",\"video\":false,\"vote_average\":7.1,\"vote_count\":17,\"year\":\"2019\"}",
	"Files": [{
		"Name": "Terra.Willy.Planète.Inconnue.aka.Astro.Kid.2019.BDREMUX.1080p.seleZen.mkv",
		"Link": "\/torrent\/view\/030c1eef674dc5ed38f3b429b1cea1523360f07a\/Terra.Willy.Planète.Inconnue.aka.Astro.Kid.2019.BDREMUX.1080p.seleZen.mkv",
		"Play": "\/torrent\/play?link=magnet:?xt=urn:btih:030c1eef674dc5ed38f3b429b1cea1523360f07a&dn=%D0%9C%D0%B0%D1%83%D0%B3%D0%BB%D0%B8+%D0%B4%D0%B8%D0%BA%D0%BE%D0%B9+%D0%BF%D0%BB%D0%B0%D0%BD%D0%B5%D1%82%D1%8B+%2F+Terra+Willy%3A+Plan%C3%A8te+inconnue+%2F+Astro+Kid+%282019%29+BDRemux+1080p+%D0%BE%D1%82+%D1%81%D0%B5%D0%BB%D0%B5%D0%B7%D0%B5%D0%BD%D1%8C+%7C+iTunes&tr=udp%3A%2F%2Fopentor.org%3A2710&tr=udp%3A%2F%2Fopentor.org%3A2710&tr=http%3A%2F%2Fretracker.local%2Fannounce&file=0",
		"Preload": "\/torrent\/preload\/030c1eef674dc5ed38f3b429b1cea1523360f07a\/Terra.Willy.Planète.Inconnue.aka.Astro.Kid.2019.BDREMUX.1080p.seleZen.mkv",
		"Size": 16653284947,
		"Viewed": true
	}]
}
 */

class Torrents : VideoProvider() {
    override fun get(): List<Torrent> {
        if (Api.serverEcho().isNotEmpty()) {
            val torrs = Api.torrentList()
            val rets = mutableListOf<Torrent>()
            torrs.forEach { torrJS ->

                var name = torrJS.get("Name", "")
                val magnet = torrJS.get("Magnet", "")
                val hash = torrJS.get("Hash", "")
                val length = torrJS.get("Length", 0L)
                val info = torrJS.get("Info", "")
                var entity: Entity? = null
                var poster = "http://tor-serve.surge.sh/ep.png"
                if (info.isNotEmpty()) {
                    try {
                        val gson = Gson()
                        val ent = gson.fromJson<Entity>(info, Entity::class.java)
                        ent?.let {
                            it.title?.let { name = it }
                            it.poster_path?.let { poster = it }
                            entity = ent
                        }
                    } catch (e: Exception) {
                    }
                }
                val files = torrJS.js.optJSONArray("Files")?.length() ?: 0
                if (name.isNotEmpty() && magnet.isNotEmpty())
                    rets.add(Torrent(
                            name,
                            magnet,
                            hash.toUpperCase(),
                            ByteFmt.byteFmt(length),
                            poster,
                            files.toString(),
                            entity
                    ))
            }
            return rets
        }
        return emptyList()
    }
}