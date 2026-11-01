
package com.campuspulse.app;

import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import android.view.View;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.campuspulse.app.databinding.ActivityNoticeDetailBinding;

import java.util.Date;

public class NoticeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "n_title";
    public static final String EXTRA_BODY = "n_body";
    public static final String EXTRA_CATEGORY = "n_category";
    public static final String EXTRA_PRIORITY = "n_priority";
    public static final String EXTRA_AUTHOR = "n_author";
    public static final String EXTRA_TIME = "n_time";
    private boolean saved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityNoticeDetailBinding b =
                ActivityNoticeDetailBinding.inflate(getLayoutInflater());

        setContentView(b.getRoot());

        String priority =
                getIntent().getStringExtra(EXTRA_PRIORITY);

        NoticeModel n = new NoticeModel();
        n.setCategory(getIntent().getStringExtra(EXTRA_CATEGORY));
        n.setPriority(priority);

        int color = NoticeModel.priorityColor(priority);

        b.tvBadge.setText(NoticeModel.badgeText(n));
        b.tvBadge.setTextColor(color);

        b.tvBadge.setBackgroundTintList(
                ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(color, 0x33)
                )
        );

        b.tvTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        b.tvBody.setText(getIntent().getStringExtra(EXTRA_BODY));

        long t = getIntent().getLongExtra(EXTRA_TIME, -1L);

        String when = TimeUtil.fullDate(
                t < 0 ? null : new Date(t)
        );

        String author = getIntent().getStringExtra(EXTRA_AUTHOR);

        b.tvMeta.setText(
                (author == null ? "Administration" : author)
                        + "  •  " + when
        );

        b.btnBack.setOnClickListener(v -> finish());
        String noticeId = getIntent().getStringExtra(NoticeAlerts.EXTRA_ID);
        FirebaseUser cu = FirebaseAuth.getInstance().getCurrentUser();
        if (noticeId != null && cu != null) setupSave(b, noticeId, cu.getUid());
    }
    private void setupSave(ActivityNoticeDetailBinding b, String noticeId, String uid) {
        CacheRepository repo = CacheRepository.get(this);
        b.btnSave.setVisibility(View.VISIBLE);
        repo.isBookmarked(uid, noticeId, yes -> {
            saved = yes;
            paintStar(b);
        });
        b.btnSave.setOnClickListener(v -> {
            b.btnSave.setEnabled(false);
            repo.toggleBookmark(uid, noticeId, now -> {
                b.btnSave.setEnabled(true);
                if (now == null) {
                    Toast.makeText(this, "Can't save yet. Open the Notices list once while online.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                saved = now;
                paintStar(b);
                Toast.makeText(this, saved ? "Saved for offline reading ★" : "Removed from saved",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void paintStar(ActivityNoticeDetailBinding b) {
        b.btnSave.setImageResource(saved ? R.drawable.ic_star : R.drawable.ic_star_border);
    }
}
