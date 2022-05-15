package sdk.chat.ui.chat.model;

import androidx.annotation.NonNull;

import com.stfalcon.chatkit.commons.models.IDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sdk.chat.core.dao.Message;
import sdk.chat.core.dao.Thread;
import sdk.chat.core.dao.User;
import sdk.chat.core.events.EventType;
import sdk.chat.core.events.NetworkEvent;
import sdk.chat.core.session.ChatSDK;
import sdk.chat.ui.ChatSDKUI;
import sdk.chat.ui.R;
import sdk.guru.common.DisposableMap;

public class ThreadHolder implements IDialog<MessageHolder> {

    protected Thread thread;

    protected List<UserHolder> users = new ArrayList<>();
    protected Set<String> userIds = new HashSet<>();

    protected MessageHolder lastMessage = null;
    protected int unreadCount = -1;
    protected Date creationDate;
    protected String displayName;
    protected DisposableMap dm = new DisposableMap();

    protected boolean isDirty;

    protected String typingText = null;

    public ThreadHolder(Thread thread) {
        this.thread = thread;
        creationDate = thread.getCreationDate();
        update();

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(
                        EventType.MessageReadReceiptUpdated,
                        EventType.MessageUpdated,
                        EventType.ThreadMessagesUpdated,
                        EventType.ThreadRead,
                        EventType.MessageAdded,
                        EventType.MessageRemoved))
                .filter(NetworkEvent.filterThreadEntityID(getId()))
                .subscribe(networkEvent -> {
                    updateUnreadCount();
                    updateLastMessage();
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.UserMetaUpdated))
                .filter(NetworkEvent.filterThreadContainsUser(thread))
                .subscribe(networkEvent -> {
                    updateDisplayName();
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.UserPresenceUpdated))
                .filter(NetworkEvent.filterThreadContainsUser(thread))
                .subscribe(networkEvent -> {
                    updateUserPresence();
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(
                        EventType.ThreadUserAdded,
                        EventType.ThreadUserRemoved,
                        EventType.ThreadUserUpdated))
                .filter(NetworkEvent.filterThreadEntityID(getId()))
                .subscribe(networkEvent -> {
                    updateDisplayName();
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.TypingStateUpdated))
                .filter(NetworkEvent.filterThreadEntityID(getId()))
                .subscribe(networkEvent -> {
                    if (networkEvent.getText() != null) {
                        typingText = networkEvent.getText();
                        typingText += ChatSDK.getString(R.string.typing);
                    } else {
                        typingText = null;
                    }
                    isDirty = true;
                }));

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.filterType(EventType.ThreadMetaUpdated))
                .filter(NetworkEvent.filterThreadEntityID(getId()))
                .subscribe(networkEvent -> {
                    updateDisplayName();
                }));

    }

    public void update() {
        updateLastMessage();
        updateDisplayName();
        updateUsers();
        updateUnreadCount();
    }

    public void updateUserPresence() {
        boolean isDirty = false;
        for (UserHolder user: users) {
            user.updateIsOnline();
            if (user.isDirty()) {
                isDirty = true;
            }
        }
        this.isDirty = this.isDirty || isDirty;
    }

    public void updateUnreadCount() {
        int count = thread.getUnreadMessagesCount();
        if (!isDirty && count != unreadCount) {
            isDirty = true;
        }
        unreadCount = count;
    }

    public void updateUsers() {
        Set<String> newUserIds = new HashSet<>();
        for (User user: thread.getUsers()) {
            if (!user.isMe()) {
                newUserIds.add(user.getEntityID());
            }
        }
        boolean isDirty = !userIds.equals(newUserIds);
        if (isDirty) {
            userIds = newUserIds;
            users.clear();
            for (User user: thread.getUsers()) {
                if (!user.isMe()) {
                    users.add(ChatSDKUI.provider().holderProvider().getUserHolder(user));
                }
            }
            this.isDirty = isDirty;
        }
    }

    public void updateDisplayName() {
        String newName = thread.getDisplayName();
        if (!isDirty) {
            isDirty = !newName.equals(displayName);
        }
        displayName = newName;
    }

    public void updateLastMessage() {
        Message message = thread.lastMessage();

        if (!isDirty) {
            String lastMessageId = lastMessage != null ? lastMessage.getId() : "";
            String messageId = message != null ? message.getEntityID() : "";
            isDirty = !messageId.equals(lastMessageId);
        }

        if (message != null) {
            lastMessage = ChatSDKUI.shared().getMessageRegistrationManager().onNewMessageHolder(message);
        } else {
            lastMessage = null;
        }
    }

    @Override
    public String getId() {
        return thread.getEntityID();
    }

    public void markRead() {
        if (unreadCount != -1) {
            unreadCount = -1;
            isDirty = true;
        }
    }

    @Override
    public String getDialogPhoto() {
        return null;
//
//        String url = thread.getImageUrl();
//        if (url == null) {
//            if (getUsers().size() == 1) {
//                url = getUsers().get(0).getAvatar();
//            }
//        }
//
//        return url;
    }

    @Override
    public String getDialogName() {
        return displayName;
    }

    @Override
    public List<UserHolder> getUsers() {
        if (users == null) {
            updateUsers();
        }
        return users;
    }

    @Override
    public MessageHolder getLastMessage() {
        if (lastMessage == null) {
            updateLastMessage();
        }
        if (lastMessage != null) {
            lastMessage.setTypingText(typingText);
        }
        return lastMessage;
    }

    @Override
    public void setLastMessage(MessageHolder message) {
        lastMessage = message;
        unreadCount = -1;
    }

    @Override
    public int getUnreadCount() {
        if (typingText != null) {
            return 0;
        }
        if (unreadCount == -1) {
            updateUnreadCount();
        }
        return unreadCount == -1 ? 0 : unreadCount;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ThreadHolder && getId().equals(((ThreadHolder)object).getId());
    }

    public Thread getThread() {
        return thread;
    }

    public @NonNull Date getDate() {
        return thread.orderDate();
    }

    public Long getWeight() {
        return thread.getWeight();
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void markClean() {
        isDirty = false;
    }

}
