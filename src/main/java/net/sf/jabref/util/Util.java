/*  Copyright (C) 2003-2016 JabRef contributors.
    Copyright (C) 2015 Oliver Kopp

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

// created by : Morten O. Alver 2003

package net.sf.jabref.util;

import net.sf.jabref.Globals;
import net.sf.jabref.JabRefPreferences;
import net.sf.jabref.MetaData;
import net.sf.jabref.exporter.layout.Layout;
import net.sf.jabref.exporter.layout.LayoutHelper;
import net.sf.jabref.external.ExternalFileType;
import net.sf.jabref.external.ExternalFileTypes;
import net.sf.jabref.external.RegExpFileSearch;
import net.sf.jabref.external.UnknownExternalFileType;
import net.sf.jabref.groups.structure.AbstractGroup;
import net.sf.jabref.groups.structure.KeywordGroup;
import net.sf.jabref.gui.*;
import net.sf.jabref.gui.keyboard.KeyBinding;
import net.sf.jabref.gui.preftabs.ImportSettingsTab;
import net.sf.jabref.gui.undo.NamedCompound;
import net.sf.jabref.gui.undo.UndoableFieldChange;
import net.sf.jabref.gui.worker.AbstractWorker;
import net.sf.jabref.gui.worker.CallBack;
import net.sf.jabref.gui.worker.Worker;
import net.sf.jabref.logic.l10n.Localization;
import net.sf.jabref.logic.labelPattern.LabelPatternUtil;
import net.sf.jabref.logic.util.date.EasyDateFormat;
import net.sf.jabref.logic.util.io.FileNameCleaner;
import net.sf.jabref.logic.util.io.FileUtil;
import net.sf.jabref.logic.util.strings.StringUtil;
import net.sf.jabref.logic.util.strings.UnicodeToReadableCharMap;
import net.sf.jabref.model.database.BibDatabase;
import net.sf.jabref.model.entry.BibEntry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.swing.*;
import javax.swing.undo.AbstractUndoableEdit;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * utility functions
 */
public class Util {

    private static final Log LOGGER = LogFactory.getLog(Util.class);

    private static final EasyDateFormat dateFormatter = new EasyDateFormat();

    public static final String ARXIV_LOOKUP_PREFIX = "http://arxiv.org/abs/";

    private static final UnicodeToReadableCharMap UNICODE_CHAR_MAP = new UnicodeToReadableCharMap();

    /**
     * This method returns a String similar to the one passed in, except that it is molded into a form that is
     * acceptable for bibtex.
     * <p>
     * Watch-out that the returned string might be of length 0 afterwards.
     *
     * @param key mayBeNull
     */
    public static String checkLegalKey(String key) {
        if (key == null) {
            return null;
        }
        return checkLegalKey(key,
                JabRefPreferences.getInstance().getBoolean(JabRefPreferences.ENFORCE_LEGAL_BIBTEX_KEY));
    }

    /**
     * Replace non-English characters like umlauts etc. with a sensible letter or letter combination that bibtex can
     * accept. The basis for replacement is the HashMap UnicodeToReadableCharMap.
     */
    public static String replaceSpecialCharacters(String s) {
        for (Map.Entry<String, String> chrAndReplace : net.sf.jabref.util.Util.UNICODE_CHAR_MAP.entrySet()) {
            s = s.replaceAll(chrAndReplace.getKey(), chrAndReplace.getValue());
        }
        return s;
    }

    /**
     * This method returns a String similar to the one passed in, except that it is molded into a form that is
     * acceptable for bibtex.
     * <p>
     * Watch-out that the returned string might be of length 0 afterwards.
     *
     * @param key             mayBeNull
     * @param enforceLegalKey make sure that the key is legal in all respects
     */
    public static String checkLegalKey(String key, boolean enforceLegalKey) {
        if (key == null) {
            return null;
        }
        if (!enforceLegalKey) {
            // User doesn't want us to enforce legal characters. We must still look
            // for whitespace and some characters such as commas, since these would
            // interfere with parsing:
            StringBuilder newKey = new StringBuilder();
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (!Character.isWhitespace(c) && (c != '{') && (c != '\\') && (c != '"') && (c != '}') && (c != ',') && (c != '(') && (c != ')')) {
                    newKey.append(c);
                }
            }
            return newKey.toString();

        }
        StringBuilder newKey = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!Character.isWhitespace(c) && (c != '#') && (c != '{') && (c != '\\') && (c != '"') && (c != '}')
                    && (c != '~') && (c != ',') && (c != '^') && (c != '\'')  && (c != '(') && (c != ')')) {
                newKey.append(c);
            }
        }

        // Replace non-English characters like umlauts etc. with a sensible
        // letter or letter combination that bibtex can accept.

        return net.sf.jabref.util.Util.replaceSpecialCharacters(newKey.toString());
    }

    public static List<String[]> parseMethodsCalls(String calls) throws RuntimeException {

        List<String[]> result = new ArrayList<>();

        char[] c = calls.toCharArray();

        int i = 0;

        while (i < c.length) {

            int start = i;
            if (Character.isJavaIdentifierStart(c[i])) {
                i++;
                while ((i < c.length) && (Character.isJavaIdentifierPart(c[i]) || (c[i] == '.'))) {
                    i++;
                }
                if ((i < c.length) && (c[i] == '(')) {

                    String method = calls.substring(start, i);

                    // Skip the brace
                    i++;

                    if (i < c.length) {
                        if (c[i] == '"') {
                            // Parameter is in format "xxx"

                            // Skip "
                            i++;

                            int startParam = i;
                            i++;
                            boolean escaped = false;
                            while (((i + 1) < c.length) && !(!escaped && (c[i] == '"') && (c[i + 1] == ')'))) {
                                if (c[i] == '\\') {
                                    escaped = !escaped;
                                } else {
                                    escaped = false;
                                }
                                i++;

                            }

                            String param = calls.substring(startParam, i);

                            result.add(new String[]{method, param});
                        } else {
                            // Parameter is in format xxx

                            int startParam = i;

                            while ((i < c.length) && (c[i] != ')')) {
                                i++;
                            }

                            String param = calls.substring(startParam, i);

                            result.add(new String[]{method, param});

                        }
                    } else {
                        // Incorrectly terminated open brace
                        result.add(new String[]{method});
                    }
                } else {
                    String method = calls.substring(start, i);
                    result.add(new String[]{method});
                }
            }
            i++;
        }

        return result;
    }

    /**
     * Takes a string that contains bracketed expression and expands each of these using getFieldAndFormat.
     * <p>
     * Unknown Bracket expressions are silently dropped.
     *
     * @param bracketString
     * @param entry
     * @param database
     * @return
     */
    private static final Pattern squareBracketsPattern = Pattern.compile("\\[.*?\\]");

    public static String expandBrackets(String bracketString, BibEntry entry, BibDatabase database) {
        Matcher m = Util.squareBracketsPattern.matcher(bracketString);
        StringBuffer s = new StringBuffer();
        while (m.find()) {
            String replacement = Optional.ofNullable(getFieldAndFormat(m.group(), entry, database)).orElse("");
            m.appendReplacement(s, replacement);
        }
        m.appendTail(s);

        return s.toString();
    }

    /**
     * Sets empty or non-existing owner fields of bibtex entries inside a List to a specified default value. Timestamp
     * field is also set. Preferences are checked to see if these options are enabled.
     *
     * @param bibs List of bibtex entries
     */
    public static void setAutomaticFields(Collection<BibEntry> bibs, boolean overwriteOwner, boolean overwriteTimestamp, boolean markEntries) {

        boolean globalSetOwner = Globals.prefs.getBoolean(JabRefPreferences.USE_OWNER);
        boolean globalSetTimeStamp = Globals.prefs.getBoolean(JabRefPreferences.USE_TIME_STAMP);

        // Do not need to do anything if all options are disabled
        if (!(globalSetOwner || globalSetTimeStamp || markEntries)) {
            return;
        }

        String timeStampField = Globals.prefs.get(JabRefPreferences.TIME_STAMP_FIELD);
        String defaultOwner = Globals.prefs.get(JabRefPreferences.DEFAULT_OWNER);
        String timestamp = net.sf.jabref.util.Util.dateFormatter.getCurrentDate();

        // Iterate through all entries
        for (BibEntry curEntry : bibs) {
            boolean setOwner = globalSetOwner && (overwriteOwner || (!curEntry.hasField(InternalBibtexFields.OWNER)));
            boolean setTimeStamp = globalSetTimeStamp && (overwriteTimestamp || (!curEntry.hasField(timeStampField)));
            net.sf.jabref.util.Util.setAutomaticFields(curEntry, setOwner, defaultOwner, setTimeStamp, timeStampField, timestamp);
            if (markEntries) {
                EntryMarker.markEntry(curEntry, EntryMarker.IMPORT_MARK_LEVEL, false, new NamedCompound(""));
            }
        }
    }

    /**
     * Sets empty or non-existing owner fields of a bibtex entry to a specified default value. Timestamp field is also
     * set. Preferences are checked to see if these options are enabled.
     *
     * @param entry              The entry to set fields for.
     * @param overwriteOwner     Indicates whether owner should be set if it is already set.
     * @param overwriteTimestamp Indicates whether timestamp should be set if it is already set.
     */
    public static void setAutomaticFields(BibEntry entry, boolean overwriteOwner, boolean overwriteTimestamp) {
        String defaultOwner = Globals.prefs.get(JabRefPreferences.DEFAULT_OWNER);
        String timestamp = net.sf.jabref.util.Util.dateFormatter.getCurrentDate();
        String timeStampField = Globals.prefs.get(JabRefPreferences.TIME_STAMP_FIELD);
        boolean setOwner = Globals.prefs.getBoolean(JabRefPreferences.USE_OWNER)
                && (overwriteOwner || (!entry.hasField(InternalBibtexFields.OWNER)));
        boolean setTimeStamp = Globals.prefs.getBoolean(JabRefPreferences.USE_TIME_STAMP)
                && (overwriteTimestamp || (!entry.hasField(timeStampField)));

        net.sf.jabref.util.Util.setAutomaticFields(entry, setOwner, defaultOwner, setTimeStamp, timeStampField, timestamp);
    }

    private static void setAutomaticFields(BibEntry entry, boolean setOwner, String owner, boolean setTimeStamp, String timeStampField, String timeStamp) {

        // Set owner field if this option is enabled:
        if (setOwner) {
            // No or empty owner field?
            // if (entry.getField(Globals.OWNER) == null
            // || ((String) entry.getField(Globals.OWNER)).isEmpty()) {
            // Set owner field to default value
            entry.setField(InternalBibtexFields.OWNER, owner);
            // }
        }

        if (setTimeStamp) {
            entry.setField(timeStampField, timeStamp);
        }
    }

    /**
     * This method looks up what kind of external binding is used for the given field, and constructs on OpenFileFilter
     * suitable for browsing for an external file.
     *
     * @param fieldName The BibTeX field in question.
     * @return The file filter.
     */
    public static OpenFileFilter getFileFilterForField(String fieldName) {
        String s = InternalBibtexFields.getFieldExtras(fieldName);
        final String ext = "." + fieldName.toLowerCase();
        OpenFileFilter off;
        if (InternalBibtexFields.EXTRA_BROWSE_DOC_ZIP.equals(s)) {
            off = new OpenFileFilter(new String[]{ext, ext + ".gz", ext + ".bz2"});
        } else {
            off = new OpenFileFilter(new String[]{ext});
        }
        return off;
    }

    /**
     * Run an AbstractWorker's methods using Spin features to put each method on the correct thread.
     *
     * @param worker The worker to run.
     * @throws Throwable
     */
    public static void runAbstractWorker(AbstractWorker worker) throws Throwable {
        // This part uses Spin's features:
        Worker wrk = worker.getWorker();
        // The Worker returned by getWorker() has been wrapped
        // by Spin.off(), which makes its methods be run in
        // a different thread from the EDT.
        CallBack clb = worker.getCallBack();

        worker.init(); // This method runs in this same thread, the EDT.
        // Useful for initial GUI actions, like printing a message.

        // The CallBack returned by getCallBack() has been wrapped
        // by Spin.over(), which makes its methods be run on
        // the EDT.
        wrk.run(); // Runs the potentially time-consuming action
        // without freezing the GUI. The magic is that THIS line
        // of execution will not continue until run() is finished.
        clb.update(); // Runs the update() method on the EDT.
    }

    /**
     * Determines filename provided by an entry in a database
     *
     * @param database the database, where the entry is located
     * @param entry    the entry to which the file should be linked to
     * @return a suggested fileName
     */
    public static String getLinkedFileName(BibDatabase database, BibEntry entry) {
        String targetName = entry.getCiteKey() == null ? "default" : entry.getCiteKey();
        StringReader sr = new StringReader(Globals.prefs.get(ImportSettingsTab.PREF_IMPORT_FILENAMEPATTERN));
        Layout layout = null;
        try {
            layout = new LayoutHelper(sr).getLayoutFromText();
        } catch (IOException e) {
            LOGGER.info("Wrong format " + e.getMessage(), e);
        }
        if (layout != null) {
            targetName = layout.doLayout(entry, database);
        }
        //Removes illegal characters from filename
        targetName = FileNameCleaner.cleanFileName(targetName);
        return targetName;
    }

    /**
     * Updating a field will result in the entry being reformatted on save
     *
     * @param ce indicates the undo named compound. May be null
     */
    public static void updateField(BibEntry be, String field, String newValue, NamedCompound ce) {
        net.sf.jabref.util.Util.updateField(be, field, newValue, ce, false);
    }

    /**
     * Updating a non-displayable field does not result in the entry being reformatted on save
     *
     * @param ce indicates the undo named compound. May be null
     */
    public static void updateNonDisplayableField(BibEntry be, String field, String newValue, NamedCompound ce) {
        boolean changed = be.hasChanged();
        net.sf.jabref.util.Util.updateField(be, field, newValue, ce, false);
        be.setChanged(changed);
    }

    /**
     * @param ce indicates the undo named compound. May be null
     */
    public static void updateField(BibEntry be, String field, String newValue, NamedCompound ce, Boolean nullFieldIfValueIsTheSame) {
        String oldValue = be.getField(field);
        if (nullFieldIfValueIsTheSame && (oldValue != null) && oldValue.equals(newValue)) {
            // if oldValue == newValue then reset field if required by parameter
            newValue = null;
        }
        if ((oldValue == null) && (newValue == null)) {
            return;
        }
        if ((oldValue == null) || !oldValue.equals(newValue)) {
            if (newValue == null) {
                be.clearField(field);
            } else {
                be.setField(field, newValue);
            }
            if (ce != null) {
                ce.addEdit(new UndoableFieldChange(be, field, oldValue, newValue));
            }
        }
    }

    /**
     * Binds ESC-Key to cancel button
     *
     * @param rootPane     the pane to bind the action to. Typically, this variable is retrieved by this.getRootPane();
     * @param cancelAction the action to bind
     */
    // TODO: move to GUI
    public static void bindCloseDialogKeyToCancelAction(JRootPane rootPane, Action cancelAction) {
        InputMap im = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rootPane.getActionMap();
        im.put(Globals.getKeyPrefs().getKey(KeyBinding.CLOSE_DIALOG), "close");
        am.put("close", cancelAction);
    }

    /**
     * Warns the user of undesired side effects of an explicit assignment/removal of entries to/from this group.
     * Currently there are four types of groups: AllEntriesGroup, SearchGroup - do not support explicit assignment.
     * ExplicitGroup - never modifies entries. KeywordGroup - only this modifies entries upon assignment/removal.
     * Modifications are acceptable unless they affect a standard field (such as "author") besides the "keywords" field.
     *
     * @param parent The Component used as a parent when displaying a confirmation dialog.
     * @return true if the assignment has no undesired side effects, or the user chose to perform it anyway. false
     * otherwise (this indicates that the user has aborted the assignment).
     */
    public static boolean warnAssignmentSideEffects(List<AbstractGroup> groups, Component parent) {
        List<String> affectedFields = new ArrayList<>();
        for (AbstractGroup group : groups) {
            if (group instanceof KeywordGroup) {
                KeywordGroup kg = (KeywordGroup) group;
                String field = kg.getSearchField().toLowerCase();
                if ("keywords".equals(field)) {
                    continue; // this is not undesired
                }
                for (int i = 0, len = InternalBibtexFields.numberOfPublicFields(); i < len; ++i) {
                    if (field.equals(InternalBibtexFields.getFieldName(i))) {
                        affectedFields.add(field);
                        break;
                    }
                }
            }
        }
        if (affectedFields.isEmpty()) {
            return true; // no side effects
        }

        // show a warning, then return
        StringBuffer message = new StringBuffer("This action will modify the following field(s)\n" + "in at least one entry each:\n");
        for (String affectedField : affectedFields) {
            message.append(affectedField).append('\n');
        }
        message.append("This could cause undesired changes to " + "your entries, so it is\nrecommended that you change the grouping field " + "in your group\ndefinition to \"keywords\" or a non-standard name." + "\n\nDo you still want to continue?");
        int choice = JOptionPane.showConfirmDialog(parent, message, Localization.lang("Warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice != JOptionPane.NO_OPTION;

        // if (groups instanceof KeywordGroup) {
        // KeywordGroup kg = (KeywordGroup) groups;
        // String field = kg.getSearchField().toLowerCase();
        // if (field.equals("keywords"))
        // return true; // this is not undesired
        // for (int i = 0; i < GUIGlobals.ALL_FIELDS.length; ++i) {
        // if (field.equals(GUIGlobals.ALL_FIELDS[i])) {
        // // show a warning, then return
        // String message = Globals ...
        // .lang(
        // "This action will modify the \"%0\" field "
        // + "of your entries.\nThis could cause undesired changes to "
        // + "your entries, so it is\nrecommended that you change the grouping
        // field "
        // + "in your group\ndefinition to \"keywords\" or a non-standard name."
        // + "\n\nDo you still want to continue?",
        // field);
        // int choice = JOptionPane.showConfirmDialog(parent, message,
        // Globals.lang("Warning"), JOptionPane.YES_NO_OPTION,
        // JOptionPane.WARNING_MESSAGE);
        // return choice != JOptionPane.NO_OPTION;
        // }
        // }
        // }
        // return true; // found no side effects
    }

    public static boolean updateTimeStampIsSet() {
        return Globals.prefs.getBoolean(JabRefPreferences.USE_TIME_STAMP) && Globals.prefs.getBoolean(JabRefPreferences.UPDATE_TIMESTAMP);
    }

    /**
     * Updates the timestamp of the given entry, nests the given undaoableEdit in a named compound, and returns that
     * named compound
     */
    public static NamedCompound doUpdateTimeStamp(BibEntry entry, AbstractUndoableEdit undoableEdit) {
        NamedCompound ce = new NamedCompound(undoableEdit.getPresentationName());
        ce.addEdit(undoableEdit);
        String timeStampField = Globals.prefs.get(JabRefPreferences.TIME_STAMP_FIELD);
        String timestamp = net.sf.jabref.util.Util.dateFormatter.getCurrentDate();
        net.sf.jabref.util.Util.updateField(entry, timeStampField, timestamp, ce);
        return ce;
    }

    /**
     * Shortcut method if links are set without using the GUI
     *
     * @param entries  the entries for which links should be set
     * @param metaData the meta data for the BibDatabase for which links are set
     */
    public static void autoSetLinks(Collection<BibEntry> entries, MetaData metaData) {
        autoSetLinks(entries, null, null, null, metaData, null, null);
    }

    /**
     * Shortcut method for setting a single entry
     *
     * @param entry
     * @param ce
     * @param changedEntries
     * @param singleTableModel
     * @param metaData
     * @param callback
     * @param diag
     * @return
     */
    public static Runnable autoSetLinks(BibEntry entry, final NamedCompound ce, final Set<BibEntry> changedEntries, final FileListTableModel singleTableModel, final MetaData metaData, final ActionListener callback, final JDialog diag) {
        List<BibEntry> entries = new ArrayList<>(1);
        entries.add(entry);
        return autoSetLinks(entries, ce, changedEntries, singleTableModel, metaData, callback, diag);
    }

    /**
     * Automatically add links for this set of entries, based on the globally stored list of external file types. The
     * entries are modified, and corresponding UndoEdit elements added to the NamedCompound given as argument.
     * Furthermore, all entries which are modified are added to the Set of entries given as an argument.
     * <p>
     * The entries' bibtex keys must have been set - entries lacking key are ignored. The operation is done in a new
     * thread, which is returned for the caller to wait for if needed.
     *
     * @param entries          A collection of BibEntry objects to find links for.
     * @param ce               A NamedCompound to add UndoEdit elements to.
     * @param changedEntries   MODIFIED, optional. A Set of BibEntry objects to which all modified entries is added.
     *                         This is used for status output and debugging
     * @param singleTableModel UGLY HACK. The table model to insert links into. Already existing links are not
     *                         duplicated or removed. This parameter has to be null if entries.count() != 1. The hack has been
     *                         introduced as a bibtexentry does not (yet) support the function getListTableModel() and the
     *                         FileListEntryEditor editor holds an instance of that table model and does not reconstruct it after the
     *                         search has succeeded.
     * @param metaData         The MetaData providing the relevant file directory, if any.
     * @param callback         An ActionListener that is notified (on the event dispatch thread) when the search is finished.
     *                         The ActionEvent has id=0 if no new links were added, and id=1 if one or more links were added. This
     *                         parameter can be null, which means that no callback will be notified.
     * @param diag             An instantiated modal JDialog which will be used to display the progress of the autosetting. This
     *                         parameter can be null, which means that no progress update will be shown.
     * @return the thread performing the autosetting
     */
    public static Runnable autoSetLinks(final Collection<BibEntry> entries, final NamedCompound ce, final Set<BibEntry> changedEntries, final FileListTableModel singleTableModel, final MetaData metaData, final ActionListener callback, final JDialog diag) {
        final Collection<ExternalFileType> types = ExternalFileTypes.getInstance().getExternalFileTypeSelection();
        if (diag != null) {
            final JProgressBar prog = new JProgressBar(JProgressBar.HORIZONTAL, 0, types.size() - 1);
            final JLabel label = new JLabel(Localization.lang("Searching for files"));
            prog.setIndeterminate(true);
            prog.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            diag.setTitle(Localization.lang("Autosetting links"));
            diag.getContentPane().add(prog, BorderLayout.CENTER);
            diag.getContentPane().add(label, BorderLayout.SOUTH);

            diag.pack();
            diag.setLocationRelativeTo(diag.getParent());
        }

        Runnable r = new Runnable() {

            @Override
            public void run() {
                // determine directories to search in
                ArrayList<File> dirs = new ArrayList<>();
                List<String> dirsS = metaData.getFileDirectory(Globals.FILE_FIELD);
                for (String dirs1 : dirsS) {
                    dirs.add(new File(dirs1));
                }

                // determine extensions
                Collection<String> extensions = new ArrayList<>();
                for (final ExternalFileType type : types) {
                    extensions.add(type.getExtension());
                }

                // Run the search operation:
                Map<BibEntry, java.util.List<File>> result;
                if (Globals.prefs.getBoolean(JabRefPreferences.AUTOLINK_USE_REG_EXP_SEARCH_KEY)) {
                    String regExp = Globals.prefs.get(JabRefPreferences.REG_EXP_SEARCH_EXPRESSION_KEY);
                    result = RegExpFileSearch.findFilesForSet(entries, extensions, dirs, regExp);
                } else {
                    result = FileUtil.findAssociatedFiles(entries, extensions, dirs);
                }

                boolean foundAny = false;
                // Iterate over the entries:
                for (Entry<BibEntry, List<File>> entryFilePair : result.entrySet()) {
                    FileListTableModel tableModel;
                    String oldVal = entryFilePair.getKey().getField(Globals.FILE_FIELD);
                    if (singleTableModel == null) {
                        tableModel = new FileListTableModel();
                        if (oldVal != null) {
                            tableModel.setContent(oldVal);
                        }
                    } else {
                        assert entries.size() == 1;
                        tableModel = singleTableModel;
                    }
                    List<File> files = entryFilePair.getValue();
                    for (File f : files) {
                        f = FileUtil.shortenFileName(f, dirsS);
                        boolean alreadyHas = false;
                        //System.out.println("File: "+f.getPath());
                        for (int j = 0; j < tableModel.getRowCount(); j++) {
                            FileListEntry existingEntry = tableModel.getEntry(j);
                            //System.out.println("Comp: "+existingEntry.getLink());
                            if (new File(existingEntry.link).equals(f)) {
                                alreadyHas = true;
                                break;
                            }
                        }
                        if (!alreadyHas) {
                            foundAny = true;
                            ExternalFileType type;
                            Optional<String> extension = FileUtil.getFileExtension(f);
                            if (extension.isPresent()) {
                                type = ExternalFileTypes.getInstance().getExternalFileTypeByExt(extension.get());
                            } else {
                                type = new UnknownExternalFileType("");
                            }
                            FileListEntry flEntry = new FileListEntry(f.getName(), f.getPath(), type);
                            tableModel.addEntry(tableModel.getRowCount(), flEntry);

                            String newVal = tableModel.getStringRepresentation();
                            if (newVal.isEmpty()) {
                                newVal = null;
                            }
                            if (ce != null) {
                                // store undo information
                                UndoableFieldChange change = new UndoableFieldChange(entryFilePair.getKey(),
                                        Globals.FILE_FIELD, oldVal, newVal);
                                ce.addEdit(change);
                            }
                            // hack: if table model is given, do NOT modify entry
                            if (singleTableModel == null) {
                                entryFilePair.getKey().setField(Globals.FILE_FIELD, newVal);
                            }
                            if (changedEntries != null) {
                                changedEntries.add(entryFilePair.getKey());
                            }
                        }
                    }
                }

                // handle callbacks and dialog
                // FIXME: The ID signals if action was successful :/
                final int id = foundAny ? 1 : 0;
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        if (diag != null) {
                            diag.dispose();
                        }
                        if (callback != null) {
                            callback.actionPerformed(new ActionEvent(this, id, ""));
                        }
                    }
                });
            }
        };
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                // show dialog which will be hidden when the task is done
                if (diag != null) {
                    diag.setVisible(true);
                }
            }
        });
        return r;
    }

    /**
     * Automatically add links for this entry to the table model given as an argument, based on the globally stored list
     * of external file types. The entry itself is not modified. The entry's bibtex key must have been set.
     *
     * @param entry            The BibEntry to find links for.
     * @param singleTableModel The table model to insert links into. Already existing links are not duplicated or
     *                         removed.
     * @param metaData         The MetaData providing the relevant file directory, if any.
     * @param callback         An ActionListener that is notified (on the event dispatch thread) when the search is finished.
     *                         The ActionEvent has id=0 if no new links were added, and id=1 if one or more links were added. This
     *                         parameter can be null, which means that no callback will be notified. The passed ActionEvent is
     *                         constructed with (this, id, ""), where id is 1 if something has been done and 0 if nothing has been
     *                         done.
     * @param diag             An instantiated modal JDialog which will be used to display the progress of the autosetting. This
     *                         parameter can be null, which means that no progress update will be shown.
     * @return the runnable able to perform the autosetting
     */
    public static Runnable autoSetLinks(final BibEntry entry, final FileListTableModel singleTableModel, final MetaData metaData, final ActionListener callback, final JDialog diag) {
        final Collection<BibEntry> entries = new ArrayList<>();
        entries.add(entry);

        return net.sf.jabref.util.Util.autoSetLinks(entries, null, null, singleTableModel, metaData, callback, diag);
    }

    /**
     * Accepts a string like [author:lower] or [title:abbr] or [auth], whereas the first part signifies the bibtex-field
     * to get, or the key generator field marker to use, while the others are the modifiers that will be applied.
     *
     * @param fieldAndFormat
     * @param entry
     * @param database
     * @return
     */
    public static String getFieldAndFormat(String fieldAndFormat, BibEntry entry, BibDatabase database) {

        fieldAndFormat = StringUtil.stripBrackets(fieldAndFormat);

        int colon = fieldAndFormat.indexOf(':');

        String beforeColon;
        String afterColon;
        if (colon == -1) {
            beforeColon = fieldAndFormat;
            afterColon = null;
        } else {
            beforeColon = fieldAndFormat.substring(0, colon);
            afterColon = fieldAndFormat.substring(colon + 1);
        }
        beforeColon = beforeColon.trim();

        if (beforeColon.isEmpty()) {
            return null;
        }

        String fieldValue = BibDatabase.getResolvedField(beforeColon, entry, database);

        // If no field value was found, try to interpret it as a key generator field marker:
        if (fieldValue == null) {
            fieldValue = LabelPatternUtil.makeLabel(entry, beforeColon);
        }

        if (fieldValue == null) {
            return null;
        }

        if ((afterColon == null) || afterColon.isEmpty()) {
            return fieldValue;
        }

        String[] parts = afterColon.split(":");
        fieldValue = LabelPatternUtil.applyModifiers(fieldValue, parts, 0);

        return fieldValue;
    }

}
