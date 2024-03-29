package bluej.editor.moe;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.utility.DialogManager;
import bluej.utility.EscapeDialog;

public class PrintDialog extends EscapeDialog
{
    private boolean ok; // result: which button?
    private JCheckBox printLineNumbers;
    private JCheckBox printHighlighting;

    /**
     * Creates a new ProjectPrintDialog object.
     * 
     * @param parent the frame that called the print dialog
     */
    public PrintDialog(Frame parent)
    {
        super(parent, Config.getString("editor.printDialog.title"), true);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent E)
            {
                ok = false;
                setVisible(false);
            }
        });

        JPanel mainPanel = new JPanel();

        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BlueJTheme.dialogBorder);
        mainPanel.add(Box.createVerticalStrut(
                              BlueJTheme.dialogCommandButtonsVertical));

        printLineNumbers = new JCheckBox(Config.getString("editor.printDialog.printLineNumbers"));
        printLineNumbers.setSelected(true);
        mainPanel.add(printLineNumbers);
                
        printHighlighting = new JCheckBox(Config.getString("editor.printDialog.printHighlighting"));
        mainPanel.add(printHighlighting);
                
        mainPanel.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton okButton = BlueJTheme.getOkButton();
        okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) { doOK(); }        		
        });
        
        JButton cancelButton = BlueJTheme.getCancelButton();
        cancelButton.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent evt) { doCancel(); }        		
		});

        DialogManager.addOKCancelButtons(buttonPanel, okButton, cancelButton);

        getRootPane().setDefaultButton(okButton);

        mainPanel.add(buttonPanel);

        getContentPane().add(mainPanel);
        pack();

        DialogManager.centreDialog(this);
    }

    /**
     * Show this dialog and return true if "OK" was pressed, false if
     * cancelled.
     * 
     * @return the status of the print job, proceed if true, cancel if false
     */
    public boolean display()
    {
        ok = false;
        setVisible(true);

        return ok;
    }

    /**
     * Close action called when OK button is pressed.  It only sets ok boolean
     * flag to true as long as one of the check boxes is selected
     */
    public void doOK()
    {
        ok = true;
        setVisible(false);
    }

    /**
     * Close action when Cancel is pressed.
     */
    public void doCancel()
    {
        ok = false;
        setVisible(false);
    }

    /**
     * Print line numbers selection status
     * 
     * @return true if radio button is selected meaning line numbers should be
     *         printed
     */
    public boolean printLineNumbers()
    {
        return printLineNumbers.isSelected();
    }

    /**
     * Print with syntax highlighting selection status
     * 
     * @return true if radio button is selected meaning source code should be
     *         printed with syntax highlighting
     */
    public boolean printHighlighting()
    {
        return printHighlighting.isSelected();
    }
}
