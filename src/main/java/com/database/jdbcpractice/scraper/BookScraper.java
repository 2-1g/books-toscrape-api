package com.database.jdbcpractice.scraper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 앱 시작 시 books.toscrape.com을 jsoup으로 크롤링해서 category, book 테이블에 저장한다.
 */
@Component
public class BookScraper implements ApplicationRunner {

	private static final String BASE_URL = "https://books.toscrape.com/";
	private static final List<String> RATINGS = List.of("One", "Two", "Three", "Four", "Five");

	private final JdbcTemplate jdbc;

	public BookScraper(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		// 이미 저장되어 있으면 다시 크롤링하지 않는다.
		if (jdbc.queryForObject("SELECT COUNT(*) FROM book", Integer.class) > 0) {
			return;
		}

		Document home = Jsoup.connect(BASE_URL).get();
		for (Element a : home.select("ul.nav-list > li > ul > li > a")) {
			int categoryId = saveCategory(a.text().trim(), a.absUrl("href"));
			scrapeBooks(categoryId, a.absUrl("href"));
		}
	}

	/** 카테고리의 첫 페이지부터 "next" 링크가 없을 때까지 책을 모아서, 한 번의 batch로 저장한다. */
	private void scrapeBooks(int categoryId, String url) throws IOException {
		List<Object[]> rows = new ArrayList<>();

		while (url != null) {
			Document page = Jsoup.connect(url).get();

			for (Element card : page.select("article.product_pod")) {
				Element link = card.selectFirst("h3 > a");
				rows.add(new Object[] {
						categoryId,
						link.attr("title"),
						parsePrice(card.selectFirst("p.price_color").text()),
						parseRating(card.selectFirst("p.star-rating")),
						card.selectFirst("p.availability").text().trim(),
						link.absUrl("href")});
			}

			Element next = page.selectFirst("li.next > a");
			url = (next != null) ? next.absUrl("href") : null;
		}

		jdbc.batchUpdate("""
				INSERT INTO book (category_id, title, price, rating, availability, url)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (url) DO NOTHING
				""", rows);
	}

	private int saveCategory(String name, String url) {
		return jdbc.queryForObject("""
				INSERT INTO category (name, url) VALUES (?, ?)
				ON CONFLICT (name) DO UPDATE SET url = EXCLUDED.url
				RETURNING id
				""", Integer.class, name, url);
	}

	/** "£51.77" → 51.77 */
	private BigDecimal parsePrice(String text) {
		return new BigDecimal(text.replaceAll("[^0-9.]", ""));
	}

	/** class="star-rating Four" → 4 */
	private int parseRating(Element starRating) {
		for (String cls : starRating.classNames()) {
			int index = RATINGS.indexOf(cls);
			if (index >= 0) {
				return index + 1;
			}
		}
		return 0;
	}
}
