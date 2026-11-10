package com.campuspulse.app;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.campuspulse.app.databinding.DialogContactsBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContactPicker {

    public interface Listener { void onPicked(String name, String phone); }

    private static final String PERM = Manifest.permission.READ_CONTACTS;

    private final AppCompatActivity act;
    private final Listener listener;
    private final ActivityResultLauncher<String> permLauncher;

    /** Activity-r onCreate-e baniyo (registerForActivityResult-er niyom). */
    public ContactPicker(AppCompatActivity activity, Listener listener) {
        this.act = activity;
        this.listener = listener;
        this.permLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) showList();
                    else onDenied();
                });
    }

    public void pick() {
        if (ContextCompat.checkSelfPermission(act, PERM) == PackageManager.PERMISSION_GRANTED) {
            showList();
            return;
        }
        // system dialog-er age nijer bhashay karon bujhiye dei
        new MaterialAlertDialogBuilder(act)
                .setTitle("Allow access to contacts?")
                .setMessage("CampusPulse reads your contacts only so you can pick one emergency contact. "
                        + "Your contact list is never uploaded. Only the one you choose is saved.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (d, w) -> permLauncher.launch(PERM))
                .show();
    }

    private void onDenied() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(act, PERM)) {
            Toast.makeText(act, "Without contacts access, you can't pick an emergency contact.",
                    Toast.LENGTH_LONG).show();
        } else {   // sthayibhabe deny: shudhu Settings theke chalu kora jay
            new MaterialAlertDialogBuilder(act)
                    .setTitle("Contacts access is off")
                    .setMessage("Turn on the Contacts permission for CampusPulse in Settings.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + act.getPackageName()));   // implicit intent
                        try {
                            act.startActivity(i);
                        } catch (ActivityNotFoundException ignored) { }
                    })
                    .show();
        }
    }

    private void showList() {
        ContactsHelper.load(act, list -> {
            if (act.isDestroyed() || act.isFinishing()) return;
            if (list.isEmpty()) {
                Toast.makeText(act, "No contacts with phone numbers found on this phone.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            DialogContactsBinding d = DialogContactsBinding.inflate(act.getLayoutInflater());
            List<ContactsHelper.Contact> shown = new ArrayList<>(list);

            ArrayAdapter<ContactsHelper.Contact> adapter = new ArrayAdapter<ContactsHelper.Contact>(
                    act, android.R.layout.simple_list_item_2, android.R.id.text1, shown) {
                @Override
                public View getView(int pos, View cv, ViewGroup parent) {
                    View v = super.getView(pos, cv, parent);
                    ContactsHelper.Contact c = getItem(pos);
                    ((TextView) v.findViewById(android.R.id.text1)).setText(c.name);
                    ((TextView) v.findViewById(android.R.id.text2)).setText(c.phone);
                    return v;
                }
            };
            d.lvContacts.setAdapter(adapter);

            AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                    .setTitle("Choose a contact")
                    .setView(d.getRoot())
                    .setNegativeButton("Cancel", null)
                    .create();

            d.lvContacts.setOnItemClickListener((p, v, pos, id) -> {
                ContactsHelper.Contact c = shown.get(pos);
                dialog.dismiss();
                listener.onPicked(c.name, c.phone);
            });

            d.etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) {
                    String q = s.toString().trim().toLowerCase(Locale.ROOT);
                    shown.clear();
                    for (ContactsHelper.Contact c : list) {
                        if (q.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(q)
                                || c.phone.contains(q)) shown.add(c);
                    }
                    adapter.notifyDataSetChanged();
                }
            });
            dialog.show();
        });
    }
}