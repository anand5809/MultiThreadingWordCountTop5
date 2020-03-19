
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WordCountMultiThread {
	private static final int THREAD_COUNT = 4;

	private static class FileIterator implements Iterator, AutoCloseable {
		private final BufferedReader br;
		private String nextLine;

		public FileIterator() throws IOException {
			URL text = new URL("http://www.gutenberg.org/files/2600/2600-0.txt");
			br = new BufferedReader(new InputStreamReader(text.openStream()));

			nextLine = br.readLine();
		}

		@Override
		public boolean hasNext() {
			return nextLine != null;
		}

		@Override
		public String next() {
			String lineToReturn = nextLine;
			try {
				nextLine = br.readLine();
			} catch (IOException e) {
				nextLine = null;
			}
			return lineToReturn;
		}

		@Override
		public void close() throws IOException {
			br.close();
		}
	}

	private static class Transformers {
		public String[] mapToTokens(String input) {
			return input.split("[ _\\.,\\-\\+]");
		}

		private String[] filterIllegalTokens(String[] words) {
			List<String> filteredList = new ArrayList<>();
			for (String word : words) {
				if (word.matches("[a-zA-Z]+")) {
					filteredList.add(word);
				}
			}
			return filteredList.toArray(new String[filteredList.size()]);
		}

		private String[] mapToLowerCase(String[] words) {
			String[] filteredList = new String[words.length];
			for (int i = 0; i < words.length; i++) {
				filteredList[i] = words[i].toLowerCase();
			}
			return filteredList;
		}

		public synchronized void reduce(Map<String, Integer> counter, String word) {
			if (counter.containsKey(word)) {
				counter.put(word, counter.get(word) + 1);
			} else {
				counter.put(word, 1);
			}
		}
	}

	private static class TransformationThread implements Runnable {
		private Transformers tr;
		private Queue<String> dataQueue;
		private Map<String, Integer> counters;

		public TransformationThread(Transformers tr, Map<String, Integer> counters, Queue<String> dataQueue) {
			this.tr = tr;
			this.dataQueue = dataQueue;
			this.counters = counters;
		}

		@Override
		public void run() {
			while (!dataQueue.isEmpty()) {
				String line = dataQueue.poll();
				if (line != null) {
					String[] words = tr.mapToTokens(line);
					String[] legalWords = tr.filterIllegalTokens(words);
					String[] lowerCaseWords = tr.mapToLowerCase(legalWords);
					for (String word : lowerCaseWords) {
						tr.reduce(counters, word);
					}
				}
			}
		}
	}

	public static void main(final String[] args) throws Exception {
		Transformers tr = new Transformers();
		Map<String, Integer> counters = new TreeMap<>();
		final Queue<String> dataQueue = new ConcurrentLinkedQueue<>();
		new Thread() {
			@Override
			public void run() {
				try (FileIterator fc = new FileIterator()) {
					while (fc.hasNext()) {
						dataQueue.add(fc.next());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
		while (dataQueue.isEmpty()) {
			// Wait for the thread to start writing into the queue
			Thread.sleep(10);
		}
		ExecutorService es = Executors.newFixedThreadPool(THREAD_COUNT);
		for (int i = 0; i < THREAD_COUNT; i++) {
			es.execute(new TransformationThread(tr, counters, dataQueue));
		}
		es.shutdown();
		es.awaitTermination(1, TimeUnit.MINUTES);

		Set<Entry<String, Integer>> set = counters.entrySet();

		List<Entry<String, Integer>> list = new ArrayList<Entry<String, Integer>>(set);

		Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {

			@Override
			public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {

				return o2.getValue().compareTo(o1.getValue());
			}

		});

		System.out.println("Top 5 Most used words============>" + list.subList(0, 5));
		System.out.println("Total Word=======================>" + counters.size());
		System.out.println("Word Count:\n" + counters);
		

	}
}