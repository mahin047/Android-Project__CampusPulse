package com.campuspulse.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AssistantEngine {

    public static class Reply {
        public final String text, actionLabel;
        public final Intent action;

        Reply(String text, String actionLabel, Intent action) {
            this.text = text;
            this.actionLabel = actionLabel;
            this.action = action;
        }

        static Reply text(String t) { return new Reply(t, null, null); }
    }

    private final Context ctx;
    private final String uid;
    private final PlaceRepository places;
    private final CacheRepository cache;
    private final WeatherRepository weather;

    public AssistantEngine(Context c, String uid) {
        this.ctx = c.getApplicationContext();
        this.uid = uid;
        this.places = PlaceRepository.get(ctx);
        this.cache = CacheRepository.get(ctx);
        this.weather = WeatherRepository.get(ctx);
    }

    /** Callback main thread এ আসে। */
    public void answer(String raw, CacheRepository.Callback<Reply> cb) {
        final String q = raw.toLowerCase(Locale.ROOT).trim();
        final Set<String> t = tokens(q);
        places.load(list -> route(q, t, list, cb));
    }

    private void route(String q, Set<String> t, List<CampusPlace> list, CacheRepository.Callback<Reply> cb) {
        CampusPlace place = bestPlace(t, list);
        boolean asksWhere = any(t, "where", "kothay", "location", "direction", "directions", "map",
                "reach", "find", "hours", "open", "close", "phone", "call", "number", "contact")
                || q.contains("how to get");

        // 1) জরুরি অবস্থা সবার আগে
        if (any(t, "emergency", "sos", "ambulance", "fire", "danger", "harassed", "harassment",
                "attack", "unsafe", "bachao")) {
            cb.onResult(new Reply("🚨 If someone's life is in danger, call 999 now. Then open Campus SOS "
                    + "and hold the red button so campus security can see your live location.",
                    "Open Campus SOS", new Intent(ctx, SosActivity.class)));
            return;
        }
        // 2) ছোট কথাবার্তা
        if (any(t, "hi", "hello", "hey", "salam", "assalamualaikum") && t.size() <= 3) {
            cb.onResult(Reply.text("Hello! 👋 Ask me about campus places, events, notices or the weather."));
            return;
        }
        if (any(t, "thanks", "thank", "thx", "dhonnobad")) {
            cb.onResult(Reply.text("You're welcome! 😊"));
            return;
        }
        // 3) জায়গা + "কোথায়/কখন/ফোন" ধরনের প্রশ্ন
        if (place != null && asksWhere) {
            cb.onResult(placeReply(place, t));
            return;
        }
        // 4) বিষয়ভিত্তিক
        if (any(t, "weather", "temperature", "rain", "raining", "hot", "cold", "forecast", "brishti", "garom")) {
            weatherReply(cb);
            return;
        }
        if (any(t, "event", "events", "workshop", "contest", "program", "programme", "seminar")) {
            eventsReply(q, t, cb);
            return;
        }
        if (any(t, "notice", "notices", "announcement", "news", "exam", "schedule", "latest")) {
            noticesReply(cb);
            return;
        }
        if (any(t, "lost", "found", "missing", "wallet", "umbrella")) {
            cb.onResult(new Reply("Post what you lost or found, and contact the other student from the post.",
                    "Open Lost & Found", new Intent(ctx, LostFoundActivity.class)));
            return;
        }
        if (any(t, "study", "notes", "lecture", "question", "questions", "previous", "lab", "materials", "course")) {
            cb.onResult(new Reply("The Study Hub has lecture notes, previous questions, lab materials and "
                    + "important topics.", "Open Study Hub", new Intent(ctx, StudyHubActivity.class)));
            return;
        }
        if (any(t, "report", "complain", "complaint", "broken", "hazard", "incident", "problem")) {
            cb.onResult(new Reply("You can report a campus problem with a photo and location. "
                    + "Track its status there too.", "My reports", new Intent(ctx, IncidentsActivity.class)));
            return;
        }
        // 5) শুধু জায়গার নাম লিখলে
        if (place != null) {
            cb.onResult(placeReply(place, t));
            return;
        }
        if (any(t, "help", "menu", "commands")) {
            cb.onResult(Reply.text(helpText()));
            return;
        }
        cb.onResult(Reply.text("I didn't get that. I understand English and Banglish (like \"library kothay?\").\n\n"
                + helpText()));
    }

    private String helpText() {
        return "Try asking:\n• Where is the library?\n• Medical center hours\n• Any event today?\n"
                + "• Latest notice\n• How is the weather?\n• Study notes\n• Emergency help";
    }

    // ---------- জায়গা ----------
    private CampusPlace bestPlace(Set<String> t, List<CampusPlace> list) {
        CampusPlace best = null;
        int bestScore = 0;
        for (CampusPlace p : list) {
            Set<String> kw = tokens(p.keywords.toLowerCase(Locale.ROOT));
            kw.add(p.id);
            int score = 0;
            for (String w : t) if (kw.contains(w)) score++;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private Reply placeReply(CampusPlace p, Set<String> t) {
        StringBuilder sb = new StringBuilder("📍 ").append(p.name);
        if (!p.where.isEmpty()) sb.append("\n").append(p.where);
        if (!p.hours.isEmpty()) sb.append("\n🕒 ").append(p.hours);
        if (p.hasPhone()) sb.append("\n📞 ").append(p.phone);

        boolean wantsCall = any(t, "call", "phone", "number", "contact");
        if (wantsCall && p.hasPhone()) {
            return new Reply(sb.toString(), "Call " + p.phone,
                    new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p.phone))));
        }
        if (p.hasLocation()) {
            return new Reply(sb.toString(), "Open in Maps", new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:" + p.lat + "," + p.lng + "?q=" + p.lat + "," + p.lng
                            + "(" + Uri.encode(p.name) + ")")));
        }
        if (p.hasWebsite()) {
            return new Reply(sb.toString(), "Open website",
                    new Intent(Intent.ACTION_VIEW, Uri.parse(p.website)));
        }
        return Reply.text(sb.toString());
    }

    // ---------- আবহাওয়া (cache থেকে) ----------
    private void weatherReply(CacheRepository.Callback<Reply> cb) {
        weather.loadCached(r -> {
            Intent open = new Intent(ctx, WeatherActivity.class);
            if (r == null || r.weather == null) {
                cb.onResult(new Reply("I don't have the weather yet. Open the weather screen once while online.",
                        "Open weather", open));
                return;
            }
            WeatherModel w = r.weather;
            StringBuilder sb = new StringBuilder(WeatherModel.emoji(w.code)).append(" ")
                    .append(Math.round(w.temp)).append("°C, ").append(WeatherModel.describe(w.code))
                    .append(" at KUET (feels like ").append(Math.round(w.feels)).append("°C).");
            if (w.humidity >= 0) sb.append("\nHumidity ").append(w.humidity).append("%, wind ")
                    .append(Math.round(w.wind)).append(" km/h.");
            if (!w.days.isEmpty()) sb.append("\nToday: ").append(Math.round(w.days.get(0).min))
                    .append("° to ").append(Math.round(w.days.get(0).max)).append("°C.");
            if (r.fetchedAt > 0) sb.append("\n(updated ").append(TimeUtil.ago(new Date(r.fetchedAt))).append(")");
            cb.onResult(new Reply(sb.toString(), "Open weather", open));
        });
    }

    // ---------- events (SQLite থেকে) ----------
    private void eventsReply(String q, Set<String> t, CacheRepository.Callback<Reply> cb) {
        cache.loadEvents(list -> {
            Intent open = new Intent(ctx, EventsActivity.class);
            if (list.isEmpty()) {
                cb.onResult(new Reply("I don't have any events saved yet. Open Events once while online.",
                        "Open Events", open));
                return;
            }
            boolean today = t.contains("today") || t.contains("aj") || t.contains("ajke");
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (EventModel e : list) {                      // সময় অনুযায়ী ASC
                if (e.getDateTime() == null) continue;
                boolean ok = today ? sameDay(e.getDateTime()) : !e.isPast();
                if (!ok) continue;
                sb.append("• ").append(e.getTitle()).append("\n   ")
                        .append(TimeUtil.eventFull(e.getDateTime())).append("  @ ").append(e.getVenue()).append("\n");
                if (++n == 3) break;
            }
            if (n == 0) {
                sb.append(today ? "No events today. " : "No upcoming events right now. ")
                        .append("Check the Events screen for later dates.");
            } else {
                sb.insert(0, today ? "📅 Today:\n" : "📅 Upcoming events:\n");
            }
            cb.onResult(new Reply(sb.toString().trim(), "Open Events", open));
        });
    }

    private boolean sameDay(Date d) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTime(d);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ---------- notices (SQLite থেকে) ----------
    private void noticesReply(CacheRepository.Callback<Reply> cb) {
        cache.loadNotices(uid, data -> {
            Intent open = new Intent(ctx, NoticesActivity.class);
            if (data.items.isEmpty()) {
                cb.onResult(new Reply("No notices saved yet. Open Notices once while online.",
                        "Open Notices", open));
                return;
            }
            StringBuilder sb = new StringBuilder("📢 Latest notices:\n");
            for (int i = 0; i < Math.min(3, data.items.size()); i++) {
                NoticeModel n = data.items.get(i);                // নতুন আগে
                sb.append("• ").append(n.getTitle()).append("\n   ").append(n.getCategory())
                        .append("  •  ").append(TimeUtil.ago(n.getCreatedAt())).append("\n");
            }
            cb.onResult(new Reply(sb.toString().trim(), "Open Notices", open));
        });
    }

    // ---------- helpers ----------
    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String x : s.split("[^a-z0-9]+")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    private static boolean any(Set<String> t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }
}