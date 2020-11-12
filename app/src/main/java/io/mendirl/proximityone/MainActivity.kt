package io.mendirl.proximityone

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun showNewAddressDialog(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Address")

        // Get the layout inflater
        val dialogView = layoutInflater.inflate(R.layout.dialog_signin, null)

        builder.setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                sendDialogDataToActivity(
                    dialogView.findViewById<EditText>(
                        R.id.new_adress
                    ).text.toString()
                )
            }
            .setNegativeButton(R.string.nok) { dialog, _ -> dialog.cancel() }

        builder.create().show()
    }


    private fun sendDialogDataToActivity(data: String) {
        Toast.makeText(this, data, Toast.LENGTH_SHORT).show()
    }

}