package ru.yourok.torrserve.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.yourok.torrserve.R
import ru.yourok.torrserve.settings.Prefs
import ru.yourok.torrserve.settings.Session

/**
 * Пользователи, вошедшие на этом устройстве:
 * тап - сделать активным, долгий тап - выйти, кнопка - войти под другим.
 */
class UsersFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private val adapter = UsersAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View =
        inflater.inflate(R.layout.fragment_users, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs(requireContext())
        list = view.findViewById(R.id.users_list)
        empty = view.findViewById(R.id.users_empty)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        view.findViewById<View>(R.id.users_add).setOnClickListener {
            startActivityForResult(Intent(requireContext(), LoginActivity::class.java), REQ_LOGIN)
        }
        view.findViewById<View>(R.id.users_logout).setOnClickListener {
            prefs.activeSession()?.let { confirmLogout(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        (activity as? MainActivity)?.updateSidebarState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_LOGIN) {
            render()
            (activity as? MainActivity)?.updateSidebarState()
        }
    }

    private fun render() {
        val sessions = prefs.getSessions()
        val active = prefs.activeSession()?.id
        adapter.submit(sessions, active)
        empty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setActive(s: Session) {
        prefs.activeUserId = s.id
        render()
        (activity as? MainActivity)?.updateSidebarState()
    }

    private fun confirmLogout(s: Session) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.users_logout_confirm, s.username))
            .setPositiveButton(R.string.users_logout) { _, _ ->
                prefs.removeSession(s.id)
                render()
                (activity as? MainActivity)?.updateSidebarState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class UsersAdapter : RecyclerView.Adapter<UsersAdapter.VH>() {
        private var data: List<Session> = emptyList()
        private var activeId: String? = null

        fun submit(sessions: List<Session>, active: String?) {
            data = sessions
            activeId = active
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_user, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = data[position]
            holder.name.text = s.username
            holder.active.visibility = if (s.id == activeId) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { setActive(s) }
            holder.itemView.setOnLongClickListener { confirmLogout(s); true }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.user_name)
            val active: TextView = v.findViewById(R.id.user_active)
        }
    }

    companion object {
        private const val REQ_LOGIN = 42
    }
}
