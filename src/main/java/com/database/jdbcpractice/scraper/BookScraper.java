package com.database.jdbcpractice.scraper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 시작 시 books.toscrape.com을 jsoup으로 크롤링해서 category, book 테이블에 저장한다.
 */
@Component
public class BookScraper implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BookScraper.class);
	private static final String BASE_URL = "https://books.toscrape.com/";
	private static final List<String> RATINGS = List.of("One", "Two", "Three", "Four", "Five");

	private final JdbcTemplate jdbc;

	public BookScraper(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 크롤링 전체를 하나의 트랜잭션으로 묶는다. 도중에 실패하면 전부 롤백되어 DB가 빈 상태로 남고,
	 * 다음 실행에서 처음부터 다시 크롤링한다.
	 * IOException 같은 checked 예외는 기본적으로 롤백되지 않으므로 rollbackFor를 지정한다.
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void run(ApplicationArguments args) throws IOException {
		// 이미 저장되어 있으면 다시 크롤링하지 않는다.
		int saved = countBooks();
		if (saved > 0) {
			log.info("이미 책 {}권이 저장되어 있어 크롤링을 건너뜁니다", saved);
			return;
		}

		log.info("크롤링 시작: {}", BASE_URL);
		Document home = Jsoup.connect(BASE_URL).get();
		Elements categories = home.select("ul.nav-list > li > ul > li > a");
		for (Element a : categories) {
			int categoryId = saveCategory(a.text().trim(), a.absUrl("href"));
			scrapeBooks(categoryId, a.absUrl("href"));
		}
		log.info("크롤링 완료: 카테고리 {}개, 책 {}권 저장", categories.size(), countBooks());
	}

	private int countBooks() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM book", Integer.class);
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
