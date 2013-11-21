
package org.wordpress.android.ui.accounts;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.WordPressDB;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.WPAlertDialogFragment;
import org.wordpress.android.widgets.WPTextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WelcomeFragmentSignIn extends NewAccountAbstractPageFragment implements TextWatcher {
    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private EditText mUrlEditText;
    private boolean mSelfHosted;
    private WPTextView mSignInButton;
    private WPTextView mCreateAccountButton;
    private WPTextView mAddSelfHostedButton;
    private WPTextView mProgressTextSignIn;
    private ProgressBar mProgressBarSignIn;
    private List mUsersBlogsList;
    private boolean mHttpAuthRequired;
    private String mHttpUsername = "";
    private String mHttpPassword = "";

    public WelcomeFragmentSignIn() {

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater
                .inflate(R.layout.nux_fragment_welcome, container, false);

        ImageView statsIcon = (ImageView) rootView.findViewById(R.id.nux_fragment_icon);
        statsIcon.setImageResource(R.drawable.nux_icon_wp);

        WPTextView statsTitle = (WPTextView) rootView.findViewById(R.id.nux_fragment_title);
        statsTitle.setText(R.string.nux_welcome);

        final RelativeLayout urlButtonLayout = (RelativeLayout) rootView.
                findViewById(R.id.url_button_layout);

        mUsernameEditText = (EditText) rootView.findViewById(R.id.nux_username);
        mUsernameEditText.addTextChangedListener(this);
        mPasswordEditText = (EditText) rootView.findViewById(R.id.nux_password);
        mPasswordEditText.addTextChangedListener(this);
        mUrlEditText = (EditText) rootView.findViewById(R.id.nux_url);
        mSignInButton = (WPTextView) rootView.findViewById(R.id.nux_sign_in_button);
        mSignInButton.setOnClickListener(mSignInClickListener);
        mProgressBarSignIn = (ProgressBar) rootView.findViewById(R.id.nux_sign_in_progress_bar);
        mProgressTextSignIn = (WPTextView) rootView.findViewById(R.id.nux_sign_in_progress_text);

        mCreateAccountButton = (WPTextView) rootView.findViewById(R.id.nux_create_account_button);
        mCreateAccountButton.setOnClickListener(mCreateAccountListener);
        mAddSelfHostedButton = (WPTextView) rootView.findViewById(R.id.nux_add_selfhosted_button);
        mAddSelfHostedButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (urlButtonLayout.getVisibility() == View.VISIBLE) {
                    urlButtonLayout.setVisibility(View.GONE);
                    mAddSelfHostedButton.setText(getString(R.string.nux_add_selfhosted_blog));
                    mSelfHosted = false;
                } else {
                    urlButtonLayout.setVisibility(View.VISIBLE);
                    mAddSelfHostedButton.setText(getString(R.string.nux_oops_not_selfhosted_blog));
                    mSelfHosted = true;
                }
            }
        });
        return rootView;
    }

    private View.OnClickListener mCreateAccountListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent newAccountIntent = new Intent(getActivity(), NewAccountActivity.class);
            startActivityForResult(newAccountIntent, WelcomeActivity.CREATE_ACCOUNT_REQUEST);
        }
    };

    private OnClickListener mSignInClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!wpcomFieldsFilled()) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                WPAlertDialogFragment alert = WPAlertDialogFragment
                        .newInstance(getString(R.string.required_fields));
                alert.show(ft, "alert");
                return;
            }
            new SetupBlogTask().execute();
        }
    };

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (wpcomFieldsFilled()) {
            mSignInButton.setEnabled(true);
        } else {
            mSignInButton.setEnabled(false);
        }
    }

    private boolean wpcomFieldsFilled() {
        return mUsernameEditText.getText().toString().trim().length() > 0
                && mPasswordEditText.getText().toString().trim().length() > 0;
    }

    private boolean selfHostedFieldsFilled() {
        return wpcomFieldsFilled()
                && mUrlEditText.getText().toString().trim().length() > 0;
    }

    public void signInDotComUser() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        String username = settings.getString(WordPress.WPCOM_USERNAME_PREFERENCE, null);
        String password = WordPressDB.decryptPassword(settings.getString(WordPress.WPCOM_PASSWORD_PREFERENCE, null));
        if (username != null && password != null) {
            mUsernameEditText.setText(username);
            mPasswordEditText.setText(password);
            new SetupBlogTask().execute();
        }
    }

    private void startProgressSignIn(String message) {
        mProgressBarSignIn.setVisibility(View.VISIBLE);
        mProgressTextSignIn.setVisibility(View.VISIBLE);
        mSignInButton.setVisibility(View.GONE);
        mProgressBarSignIn.setEnabled(false);
        mProgressTextSignIn.setText(message);
    }

    private void updateProgressSignIn(String message) {
        mProgressTextSignIn.setText(message);
    }

    private void endProgressSignIn() {
        mProgressBarSignIn.setVisibility(View.GONE);
        mProgressTextSignIn.setVisibility(View.GONE);
        mSignInButton.setVisibility(View.VISIBLE);
    }


    private class SetupBlogTask extends AsyncTask<Void, Void, List<Object>> {
        private SetupBlog mSetupBlog;
        private String mErrorMsg;

        @Override
        protected void onPreExecute() {
            mSetupBlog = new SetupBlog();
            mSetupBlog.setUsername(mUsernameEditText.getText().toString().trim());
            mSetupBlog.setPassword(mPasswordEditText.getText().toString().trim());
            if (mSelfHosted) {
                mSetupBlog.setSelfHostedURL(mUrlEditText.getText().toString().trim());
            } else {
                mSetupBlog.setSelfHostedURL(null);
            }
            startProgressSignIn(selfHostedFieldsFilled() ? getString(R.string.attempting_configure):
                    getString(R.string.connecting_wpcom));
        }

        @Override
        protected List doInBackground(Void... args) {
            List userBlogList = mSetupBlog.getBlogList();
            if (mSetupBlog.getErrorMsgId() != -1) {
                mErrorMsg = getString(mSetupBlog.getErrorMsgId());
            }
            return userBlogList;
        }

        @Override
        protected void onPostExecute(List<Object> usersBlogsList) {
            if (mHttpAuthRequired) {
                // Prompt for http credentials
                mHttpAuthRequired = false;
                AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                alert.setTitle(R.string.http_authorization_required);

                View httpAuth = getActivity().getLayoutInflater().inflate(R.layout.alert_http_auth, null);
                final EditText usernameEditText = (EditText) httpAuth.findViewById(R.id.http_username);
                final EditText passwordEditText = (EditText) httpAuth.findViewById(R.id.http_password);
                alert.setView(httpAuth);

                alert.setPositiveButton(R.string.sign_in, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mSetupBlog.setHttpUsername(usernameEditText.getText().toString());
                        mSetupBlog.setHttpPassword(passwordEditText.getText().toString());
                        new SetupBlogTask().execute();
                    }
                });

                alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled.
                    }
                });

                alert.show();
                endProgressSignIn();
                return;
            }

            if (usersBlogsList == null && mErrorMsg != null) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                NUXDialogFragment nuxAlert = NUXDialogFragment
                        .newInstance(getString(R.string.nux_cannot_log_in), mErrorMsg,
                                getString(R.string.nux_tap_continue), R.drawable.nux_icon_alert);
                nuxAlert.show(ft, "alert");
                mErrorMsg = null;
                endProgressSignIn();
                return;
            }

            // Update wp.com credentials
            if (mSetupBlog.getXmlrpcUrl().contains("wordpress.com")) {
                SharedPreferences settings = PreferenceManager.
                        getDefaultSharedPreferences(getActivity());
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(WordPress.WPCOM_USERNAME_PREFERENCE, mSetupBlog.getUsername());
                editor.putString(WordPress.WPCOM_PASSWORD_PREFERENCE,
                        WordPressDB.encryptPassword(mSetupBlog.getPassword()));
                editor.commit();
                // Fire off a request to get an access token
                WordPress.restClient.get("me", null, null);
            }

            if (usersBlogsList != null && usersBlogsList.size() != 0) {
                mUsersBlogsList = usersBlogsList;
                SparseBooleanArray allBlogs = new SparseBooleanArray();
                for (int i = 0; i < mUsersBlogsList.size(); i++) {
                    allBlogs.put(i, true);
                }
                mSetupBlog.addBlogs(usersBlogsList, allBlogs);
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            } else {
                endProgressSignIn();
            }
        }
    }

    private class UsersBlogsArrayAdapter extends ArrayAdapter {

        public UsersBlogsArrayAdapter(Context context, int resource,
                                      List<Object> list) {
            super(context, resource, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                convertView = inflater.inflate(R.layout.blogs_row, parent, false);
            }

            Map<String, Object> blogMap = (HashMap<String, Object>) mUsersBlogsList.get(position);
            if (blogMap != null) {

                CheckedTextView blogTitleView = (CheckedTextView) convertView.findViewById(R.id.blog_title);
                String blogTitle = blogMap.get("blogName").toString();
                if (blogTitle != null && blogTitle.trim().length() > 0) {
                    blogTitleView.setText(StringUtils.unescapeHTML(blogTitle));
                } else {
                    blogTitleView.setText(blogMap.get("url").toString());
                }
            }
            return convertView;
        }
    }
}
