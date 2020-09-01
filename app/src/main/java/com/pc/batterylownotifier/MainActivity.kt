package com.pc.batterylownotifier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pc.batterylownotifier.databinding.ActivityMainBinding
import com.pc.batterylownotifier.receiver.BatteryReceiver

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var progressBar: ProgressBar? = null

    private fun setProgressBar(bar: ProgressBar) {
        progressBar = bar
    }

    private fun showProgressBar() {
        progressBar?.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        progressBar?.visibility = View.INVISIBLE
    }

    // [START declare_auth]
    private lateinit var auth: FirebaseAuth
    // [END declare_auth]

    private lateinit var db: FirebaseFirestore;

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private var signOut: MenuItem? = null
    private var revokeAccess: MenuItem? = null


    private val mBatteryReceiverListener = BatteryReceiver();
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        setProgressBar(binding.progressBar)
        setSupportActionBar(binding.toolbar)

        // Button listeners
        binding.signInButton.setOnClickListener(this)

        createNotificationChannel()
        // [START initialize_auth]
        // Initialize Firebase Auth
        auth = Firebase.auth
        // [END initialize_auth]

        db = Firebase.firestore;

        // [START config_signin]
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        // [END config_signin]

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val batteryIntentFilter = IntentFilter()
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_OKAY)
        batteryIntentFilter.addAction(Intent.ACTION_BATTERY_LOW)
        registerReceiver(mBatteryReceiverListener, batteryIntentFilter)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        signOut = menu.findItem(R.id.signOutButton)
        revokeAccess = menu.findItem(R.id.revokeAccess)
        updateUI()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.signOutButton -> signOut()
            R.id.revokeAccess -> revokeAccess()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    // [START on_start_check_user]
    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        updateUI()
    }
    // [END on_start_check_user]

    // [START onactivityresult]
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                // [START_EXCLUDE]
                updateUI()
                // [END_EXCLUDE]
            }
        }
    }
    // [END onactivityresult]

    // [START auth_with_google]
    private fun firebaseAuthWithGoogle(idToken: String) {
        // [START_EXCLUDE silent]
        showProgressBar()
        // [END_EXCLUDE]
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    updateUI()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    // [START_EXCLUDE]
                    val view = binding.mainLayout
                    // [END_EXCLUDE]
                    Snackbar.make(view, "Authentication Failed.", Snackbar.LENGTH_SHORT).show()
                    updateUI()
                }

                // [START_EXCLUDE]
                hideProgressBar()
                // [END_EXCLUDE]
            }
    }
    // [END auth_with_google]

    // [START signin]
    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    // [END signin]

    private fun signOut() {
        // Firebase sign out
        auth.signOut()

        // Google sign out
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI()
        }
    }

    private fun revokeAccess() {
        // Firebase sign out
        auth.signOut()

        // Google revoke access
        googleSignInClient.revokeAccess().addOnCompleteListener(this) {
            updateUI()
        };
        true
    }

    private fun updateUI() {
        hideProgressBar()
        val user = auth.currentUser
        if (user != null) {
            Log.d(TAG, user.toString())
            Log.d(TAG, user.email.toString())
            binding.status.text = getString(R.string.google_status_fmt, user.email)
            binding.signInButton.visibility = View.GONE
            signOut?.isVisible = true
            revokeAccess?.isVisible = true
        } else {
            binding.status.setText(R.string.signed_out)
            Log.d(TAG, "$revokeAccess $signOut")
            revokeAccess?.isVisible = false
            signOut?.isVisible = false
            binding.signInButton.visibility = View.VISIBLE
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.signInButton -> signIn()
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel(getString(R.string.channel_name), name, importance).apply {
                    description = descriptionText
                }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val RC_SIGN_IN = 9001
    }
}