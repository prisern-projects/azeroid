package com.wehavephones.xserver.requests;

import static com.wehavephones.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.wehavephones.xconnector.XInputStream;
import com.wehavephones.xconnector.XOutputStream;
import com.wehavephones.xconnector.XStreamLock;
import com.wehavephones.xserver.Atom;
import com.wehavephones.xserver.ClipboardLog;
import com.wehavephones.xserver.Property;
import com.wehavephones.xserver.SelectionManager;
import com.wehavephones.xserver.Window;
import com.wehavephones.xserver.XClient;
import com.wehavephones.xserver.errors.BadAtom;
import com.wehavephones.xserver.errors.BadWindow;
import com.wehavephones.xserver.errors.XRequestError;

import com.wehavephones.xserver.events.SelectionNotify;
import com.wehavephones.xserver.events.SelectionRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public abstract class SelectionRequests {
    public static void setSelectionOwner(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int atom = inputStream.readInt();
        int timestamp = inputStream.readInt();

        Window owner = client.xServer.windowManager.getWindow(windowId);
        if (owner == null) throw new BadWindow(windowId);
        if (!Atom.isValid(atom)) throw new BadAtom(atom);

        client.xServer.selectionManager.setSelection(atom, owner, client, timestamp);
        ClipboardLog.log("SetSelectionOwner " + ClipboardLog.atom(atom) + " window=" + windowId);
    }

    public static void getSelectionOwner(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int atom = inputStream.readInt();
        if (!Atom.isValid(atom)) throw new BadAtom(atom);
        Window owner = client.xServer.selectionManager.getSelection(atom).owner;
        ClipboardLog.log("GetSelectionOwner " + ClipboardLog.atom(atom) + " -> " + (owner != null ? owner.id : 0));

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(owner != null ? owner.id : 0);
            outputStream.writePad(20);
        }
    }

    /**
     * ConvertSelection: a selection the server owns (the phone's clipboard) is answered here,
     * TARGETS plus UTF8_STRING / TEXT / STRING; a client-owned one becomes a SelectionRequest
     * for its owner; no owner means a refusal (property None).
     */
    public static void convertSelection(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int requestorId = inputStream.readInt();
        int atom = inputStream.readInt();
        int target = inputStream.readInt();
        int property = inputStream.readInt();
        int timestamp = inputStream.readInt();

        Window requestor = client.xServer.windowManager.getWindow(requestorId);
        if (requestor == null) throw new BadWindow(requestorId);
        if (property == 0) property = target;

        SelectionManager.Selection selection = client.xServer.selectionManager.getSelection(atom);
        ClipboardLog.log("ConvertSelection " + ClipboardLog.atom(atom) + " target=" + ClipboardLog.atom(target)
                + " property=" + ClipboardLog.atom(property) + " serverOwned=" + selection.isServerOwned());
        if (selection.owner != null && !selection.isServerOwned() && selection.getClient() != null) {
            selection.getClient().sendEvent(new SelectionRequest(timestamp, selection.owner.id, requestorId, atom, target, property));
            return;
        }

        boolean served = false;
        if (selection.isServerOwned()) {
            int targets = Atom.internAtom("TARGETS");
            int utf8 = Atom.internAtom("UTF8_STRING");
            int text = Atom.internAtom("TEXT");
            int plain = Atom.internAtom("text/plain;charset=utf-8");
            int string = Atom.internAtom("STRING");
            if (target == targets) {
                int[] list = {targets, utf8, text, plain, string};
                ByteBuffer data = ByteBuffer.allocate(list.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int value : list) data.putInt(value);
                requestor.modifyProperty(property, Atom.internAtom("ATOM"), Property.Format.valueOf(32), Property.Mode.REPLACE, data.array());
                served = true;
            }
            else if (target == utf8 || target == text || target == plain) {
                requestor.modifyProperty(property, utf8, Property.Format.valueOf(8), Property.Mode.REPLACE,
                        selection.serverText.getBytes(StandardCharsets.UTF_8));
                served = true;
            }
            else if (target == string) {
                requestor.modifyProperty(property, string, Property.Format.valueOf(8), Property.Mode.REPLACE,
                        selection.serverText.getBytes(StandardCharsets.ISO_8859_1));
                served = true;
            }
        }
        ClipboardLog.log("  -> " + (served ? "served" : "refused"));
        client.sendEvent(new SelectionNotify(timestamp, requestorId, atom, target, served ? property : 0));
    }
}
