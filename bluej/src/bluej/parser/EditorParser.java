/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.parser;

import java.io.Reader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;

import javax.swing.text.Document;

import bluej.debugger.gentype.Reflective;
import bluej.editor.moe.Token;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.IntersectionTypeEntity;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.ParsedReflective;
import bluej.parser.entity.TypeEntity;
import bluej.parser.entity.UnresolvedArray;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import bluej.parser.nodes.CommentNode;
import bluej.parser.nodes.ContainerNode;
import bluej.parser.nodes.ExpressionNode;
import bluej.parser.nodes.FieldNode;
import bluej.parser.nodes.MethodNode;
import bluej.parser.nodes.ParentParsedNode;
import bluej.parser.nodes.ParsedCUNode;
import bluej.parser.nodes.ParsedNode;
import bluej.parser.nodes.ParsedTypeNode;
import bluej.parser.nodes.PkgStmtNode;
import bluej.parser.nodes.TypeInnerNode;
import bluej.parser.symtab.Selection;

/**
 * Parser which builds parse node tree.
 * 
 * @author Davin McCall
 */
public class EditorParser extends JavaParser
{
    protected Stack<ParsedNode> scopeStack = new Stack<ParsedNode>();
    
    private LocatableToken pcuStmtBegin;
    private ParsedCUNode pcuNode;
    private List<LocatableToken> commentQueue = new LinkedList<LocatableToken>();
    private List<LocatableToken> lastTypeSpec;
    private FieldNode lastField;
    private int arrayDecls;
    private String declaredPkg = "";
    
    private Map<String,JavaEntity> typeParams;
    private String lastTypeParamName;
    private List<JavaEntity> lastTypeParBounds;
    
    private List<JavaEntity> extendedTypes;
    private List<JavaEntity> implementedTypes;
    
    private Document document;
    
    private boolean gotExtends = false;
    private boolean gotImplements = false;
    
    private int currentModifiers = 0;
    
    /**
     * Constructor for use by subclasses (InfoReader).
     */
    protected EditorParser(Reader r, EntityResolver resolver)
    {
        super(r);
        pcuNode = new ParsedCUNode();
        pcuNode.setParentResolver(resolver);
    }
    
    /**
     * Constructor for an EditorParser to parse a particular document.
     * After construction the normal course of action is to call parseCU(ParsedCUNode).
     */
    public EditorParser(Document document)
    {
        super(new DocumentReader(document));
        this.document = document;
        //pcuNode = new ParsedCUNode(document);
    }
    
    protected void error(String msg)
    {
        // ignore for now
    }
    
    @Override
    public void parseCU()
    {
        scopeStack.push(pcuNode);
        super.parseCU();
        scopeStack.pop();
        completedNode(pcuNode, 0, pcuNode.getSize());
    }
    
    public void parseCU(ParsedCUNode pcuNode)
    {
        this.pcuNode = pcuNode;
        scopeStack.push(pcuNode);
        super.parseCU();
        scopeStack.pop();
        completedNode(pcuNode, 0, pcuNode.getSize());
    }

    /**
     * Convert a line and column number to an absolute position within the document
     * @param line  Line number (1..N)
     * @param col   Column number (1..N)
     * @return   The absolute position (0..N)
     */
    private int lineColToPosition(int line, int col)
    {
        if (document == null) {
            return 0;
        }
        return document.getDefaultRootElement().getElement(line - 1).getStartOffset() + col - 1;
    }

    /**
     * Close the node which is currently at the top of the node stack.
     * @param token     The token which finishes the node
     * @param included  Whether the token itself is part of the node
     */
    private void endTopNode(LocatableToken token, boolean included)
    {
        int topPos = getTopNodeOffset();
        ParsedNode top = scopeStack.pop();

        int endPos;
        if (included) {
            endPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        }
        else {
            endPos = lineColToPosition(token.getLine(), token.getColumn());
        }
        top.setNodeSize(endPos - topPos);
        
        completedNode(top, topPos, endPos - topPos);
    }

    /**
     * A node end has been reached. This method adds any appropriate comment nodes as
     * children of the new node.
     * 
     * @param node  The new node
     * @param position  The absolute position of the new node
     * @param size  The size of the new node
     */
    protected void completedNode(ParsedNode node, int position, int size)
    {
        ListIterator<LocatableToken> i = commentQueue.listIterator();
        while (i.hasNext()) {
            LocatableToken token = i.next();
            int startpos = lineColToPosition(token.getLine(), token.getColumn());
            if (startpos >= position && startpos < (position + size)) {
                Selection s = new Selection(token.getLine(), token.getColumn());
                s.extendEnd(token.getEndLine(), token.getEndColumn());
                int endpos = lineColToPosition(s.getEndLine(), s.getEndColumn());

                CommentNode cn = new CommentNode(node, getCommentColour(token));
                node.insertNode(cn, startpos - position, endpos - startpos);
                
                i.remove();
            }
        }
    }

    /**
     * Determine the appropriate colour for a comment.
     */
    private byte getCommentColour(LocatableToken token)
    {
        String text = token.getText();
        if (token.getType() == JavaTokenTypes.ML_COMMENT) {
            if (text.startsWith("/*#")) {
                return Token.COMMENT3;
            }
            if (text.startsWith("/**#")) {
                return Token.COMMENT3;
            }
            if (text.startsWith("/**")) {
                return Token.COMMENT2;
            }
            return Token.COMMENT1;
        }
        
        // Single line
        if (text.startsWith("//#")) {
            return Token.COMMENT3;
        }
        
        return Token.COMMENT1;
    }
    
    /**
     * Prepare to begin a new node at the given position (document position).
     */
    protected void beginNode(int position)
    {
        // If there are comments in the queue, and their position precedes that of the node
        // just being created, then we have to create their nodes now.
        ListIterator<LocatableToken> i = commentQueue.listIterator();
        while (i.hasNext()) {
            LocatableToken token = i.next();
            int startpos = lineColToPosition(token.getLine(), token.getColumn());
            int endpos = lineColToPosition(token.getEndLine(), token.getEndColumn());
            if (startpos >= position) {
                break;
            }
            i.remove();
            int topOffset = getTopNodeOffset();
            CommentNode cn = new CommentNode(scopeStack.peek(), getCommentColour(token));
            scopeStack.peek().insertNode(cn, startpos - topOffset, endpos - startpos);
        }
    }
    
    /**
     * Get the start position of the top node in the scope stack.
     */
    private int getTopNodeOffset()
    {
        Iterator<ParsedNode> i = scopeStack.iterator();
        if (!i.hasNext()) {
            return 0;
        }
        
        int rval = 0;
        i.next();
        while (i.hasNext()) {
            rval += i.next().getOffsetFromParent();
        }
        return rval;
    }
    
    /**
     * Join a sequence of tokens together to form a string.
     */
    protected String joinTokens(List<LocatableToken> tokens)
    {
        StringBuffer r = new StringBuffer();
        for (LocatableToken token : tokens) {
            r.append(token.getText());
        }
        return r.toString();
    }
    
    /**
     * Get the current query source - a fully qualified class name representing
     * the current context. (This is mainly used to determine what members of a
     * class are accessible).
     */
    protected Reflective currentQuerySource()
    {
        ListIterator<ParsedNode> i = scopeStack.listIterator(scopeStack.size());
        while (i.hasPrevious()) {
            ParsedNode pn = i.previous();
            if (pn.getNodeType() == ParsedNode.NODETYPE_TYPEDEF) {
                ParsedTypeNode ptn = (ParsedTypeNode)pn;
                return new ParsedReflective(ptn);
            }
        }
        
        return null;
    }
    
    
    //  -------------- Callbacks from the superclass ----------------------

    @Override
    protected void gotModifier(LocatableToken token)
    {
        switch (token.getType()) {
        case JavaTokenTypes.ABSTRACT:
            currentModifiers |= Modifier.ABSTRACT;
            break;
        case JavaTokenTypes.LITERAL_private:
            currentModifiers |= Modifier.PRIVATE;
            break;
        case JavaTokenTypes.LITERAL_public:
            currentModifiers |= Modifier.PUBLIC;
            break;
        case JavaTokenTypes.LITERAL_protected:
            currentModifiers |= Modifier.PROTECTED;
            break;
        case JavaTokenTypes.FINAL:
            currentModifiers |= Modifier.FINAL;
            break;
        case JavaTokenTypes.LITERAL_synchronized:
            currentModifiers |= Modifier.SYNCHRONIZED;
            break;
        case JavaTokenTypes.STRICTFP:
            currentModifiers |= Modifier.STRICT;
            break;
        case JavaTokenTypes.LITERAL_native:
            currentModifiers |= Modifier.NATIVE;
            break;
        case JavaTokenTypes.LITERAL_static:
            currentModifiers |= Modifier.STATIC;
            break;
        default:
        }
    }
    
    @Override
    protected void modifiersConsumed()
    {
        currentModifiers = 0;
    }
    
    @Override
    protected void beginPackageStatement(LocatableToken token)
    {
        pcuStmtBegin = token;
    }
    
    @Override
    protected void gotPackage(List<LocatableToken> pkgTokens)
    {
        super.gotPackage(pkgTokens);
        declaredPkg = joinTokens(pkgTokens);
    }

    @Override
    protected void gotImport(List<LocatableToken> tokens, boolean isStatic)
    {
        EntityResolver parentResolver = pcuNode.getParentResolver();
        if (parentResolver == null) {
            return;
        }
        
        if (isStatic) {
            // Apparently static inner classes can be imported with or without the "static" keyword
            // So, a static import imports a field and/or method and/or class.
            // That's right - the same import statement pulls in all three.
            
            // We want to pull the name out
            int newSize = tokens.size() - 2;
            String memberName = tokens.get(newSize + 1).getText();
            
            List<LocatableToken> newList = new ArrayList<LocatableToken>(newSize);
            Iterator<LocatableToken> i = tokens.iterator();
            while (newSize > 0) {
                newList.add(i.next());
                newSize--;
            }
            JavaEntity entity = ParseUtils.getTypeEntity(parentResolver,
                    currentQuerySource(), newList);
            TypeEntity tentity = (entity != null) ? entity.resolveAsType() : null;
            if (tentity != null) {
                pcuNode.getImports().addStaticImport(memberName, tentity);
            }
        }
        else {
            String memberName = tokens.get(tokens.size() - 1).getText();
            JavaEntity entity = ParseUtils.getTypeEntity(parentResolver,
                    currentQuerySource(), tokens);
            if (entity != null) {
                pcuNode.getImports().addNormalImport(memberName, entity);
            }
            // TODO we should look up the name fully qualified, that is, the first
            // component *must* be a package and not a class in the current package.
            // Be careful of inner types though (i.e. the imported type might be
            // an inner class).
        }
    }
    
    @Override
    protected void gotWildcardImport(List<LocatableToken> tokens,
            boolean isStatic)
    {
        EntityResolver parentResolver = pcuNode.getParentResolver();
        if (parentResolver == null) {
            return;
        }

        JavaEntity importEntity = ParseUtils.getTypeEntity(parentResolver,
                currentQuerySource(), tokens);
        if (importEntity == null) {
            return;
        }
        if (! isStatic) {
            pcuNode.getImports().addWildcardImport(importEntity);
        }
        else {
            TypeEntity tentity = importEntity.resolveAsType();
            if (tentity != null) {
                pcuNode.getImports().addStaticWildcardImport(tentity);
            }
        }
    }
    
    @Override
    protected void gotTypeDefName(LocatableToken nameToken)
    {
        Reflective ref = currentQuerySource();
        String prefix;
        if (ref != null) {
            prefix = ref.getName() + '$';
        }
        else {
            prefix = (declaredPkg.length() == 0) ? "" : (declaredPkg + ".");
        }
        
        ParsedNode pnode = new ParsedTypeNode(scopeStack.peek(), nameToken.getText(), prefix);
        int curOffset = getTopNodeOffset();
        LocatableToken hidden = pcuStmtBegin.getHiddenBefore();
        if (hidden != null && hidden.getType() == JavaTokenTypes.ML_COMMENT) {
            pcuStmtBegin = hidden; 
            // TODO: make certain hidden token not already consumed by prior sibling node
        }
        int insPos = lineColToPosition(pcuStmtBegin.getLine(), pcuStmtBegin.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
        
        typeParams = new HashMap<String,JavaEntity>();
        extendedTypes = new LinkedList<JavaEntity>();
        implementedTypes = new LinkedList<JavaEntity>();
    }
    
    @Override
    protected void gotTypeParam(LocatableToken idToken)
    {
        if (lastTypeParamName != null) {
            typeParams.put(lastTypeParamName,
                    IntersectionTypeEntity.getIntersectionEntity(lastTypeParBounds, pcuNode));
        }
        lastTypeParamName = idToken.getText();
        lastTypeParBounds = new ArrayList<JavaEntity>();
    }
    
    @Override
    protected void gotTypeParamBound(List<LocatableToken> tokens)
    {
        lastTypeParBounds.add(ParseUtils.getTypeEntity(scopeStack.peek(),
                currentQuerySource(), tokens));
    }
    
    @Override
    protected void beginTypeBody(LocatableToken token)
    {
        if (lastTypeParamName != null) {
            typeParams.put(lastTypeParamName,
                    IntersectionTypeEntity.getIntersectionEntity(lastTypeParBounds, pcuNode));
        }
        
        ParsedTypeNode top = (ParsedTypeNode) scopeStack.peek();
        top.setTypeParams(typeParams);
        top.setExtendedTypes(extendedTypes);
        top.setImplementedTypes(implementedTypes);
        gotExtends = false;
        gotImplements = false;
        
        TypeInnerNode bodyNode = new TypeInnerNode(scopeStack.peek());
        bodyNode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        top.insertInner(bodyNode, insPos - curOffset, 0);
        scopeStack.push(bodyNode);
    }
    
    @Override
    protected void beginForLoop(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_ITERATION);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginForLoopBody(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endForLoopBody(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_ITERATION) {
            endTopNode(token, false);
        }
    }
    
    @Override
    protected void beginWhileLoop(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_ITERATION);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginWhileLoopBody(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endWhileLoopBody(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_ITERATION) {
            endTopNode(token, false);
        }
    }
    
    @Override
    protected void beginDoWhile(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_ITERATION);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginDoWhileBody(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endDoWhileBody(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_ITERATION) {
            endTopNode(token, false);
        }
    }
        
    @Override
    protected void beginIfStmt(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_SELECTION);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginIfCondBlock(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endIfCondBlock(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_SELECTION) {
            endTopNode(token, false);
        }
    }
    
    @Override
    protected void endIfStmt(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }

    @Override
    protected void beginSwitchStmt(LocatableToken token)
    {
        beginIfStmt(token);
    }
    
    @Override
    protected void beginSwitchBlock(LocatableToken token)
    {
        ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
        loopNode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void endSwitchBlock(LocatableToken token)
    {
        endTopNode(token, false);
    }
    
    @Override
    protected void endSwitchStmt(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void beginTryCatchSmt(LocatableToken token)
    {
        ParentParsedNode tryNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_SELECTION);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(tryNode, insPos - curOffset, 0);
        scopeStack.push(tryNode);
    }
    
    @Override
    protected void beginTryBlock(LocatableToken token)
    {
        ParentParsedNode tryBlockNode = new ParentParsedNode(scopeStack.peek());
        tryBlockNode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(tryBlockNode, insPos - curOffset, 0);
        scopeStack.push(tryBlockNode);
    }
    
    @Override
    protected void endTryBlock(LocatableToken token, boolean included)
    {
        endTopNode(token, false);
    }
    
    @Override
    protected void endTryCatchStmt(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void beginStmtblockBody(LocatableToken token)
    {
        int curOffset = getTopNodeOffset();
        if (scopeStack.peek().getNodeType() == ParsedNode.NODETYPE_NONE) {
            // This is conditional, because the outer block may be a loop or selection
            // statement which already exists.
            ParentParsedNode blockNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_NONE);
            blockNode.setInner(false);
            int insPos = lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(blockNode, insPos - curOffset, 0);
            scopeStack.push(blockNode);
            curOffset = insPos;
        }
        ParentParsedNode blockInner = new ParentParsedNode(scopeStack.peek());
        blockInner.setInner(true);
        int insPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(blockInner, insPos - curOffset, 0);
        scopeStack.push(blockInner);
    }
   
    @Override
    protected void endStmtblockBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false); // inner
        if (scopeStack.peek().getNodeType() == ParsedNode.NODETYPE_NONE) {
            endTopNode(token, included);
        }
    }
    
    @Override
    protected void beginInitBlock(LocatableToken first, LocatableToken lcurly)
    {
        int curOffset = getTopNodeOffset();
        ParentParsedNode blockNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_NONE);
        blockNode.setInner(false);
        int insPos = lineColToPosition(first.getLine(), first.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(blockNode, insPos - curOffset, 0);
        scopeStack.push(blockNode);
        curOffset = insPos;

        ParentParsedNode blockInner = new ParentParsedNode(scopeStack.peek());
        blockInner.setInner(true);
        insPos = lineColToPosition(lcurly.getEndLine(), lcurly.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(blockInner, insPos - curOffset, 0);
        scopeStack.push(blockInner);
    }
    
    @Override
    protected void endInitBlock(LocatableToken rcurly, boolean included)
    {
        endStmtblockBody(rcurly, included);
    }
    
    protected void beginElement(LocatableToken token)
    {
        pcuStmtBegin = token;
    }
    
    protected void endTypeBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false);
    }
    
    protected void gotTypeDefEnd(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
        gotExtends = false;
        gotImplements = false;
    }
    
    @Override
    protected void endForLoop(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void endWhileLoop(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void endDoWhile(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }

    /*
     * We have the end of a package statement.
     */
    protected void gotPackageSemi(LocatableToken token)
    {
        Selection s = new Selection(pcuStmtBegin.getLine(), pcuStmtBegin.getColumn());
        s.extendEnd(token.getLine(), token.getColumn() + token.getLength());
        
        int startpos = lineColToPosition(s.getLine(), s.getColumn());
        int endpos = lineColToPosition(s.getEndLine(), s.getEndColumn());
        
        PkgStmtNode psn = new PkgStmtNode(pcuNode);
        beginNode(startpos);
        pcuNode.insertNode(psn, startpos, endpos - startpos);
        completedNode(psn, startpos, endpos - startpos);
    }
    
    protected void gotImportStmtSemi(LocatableToken token)
    {
        Selection s = new Selection(pcuStmtBegin.getLine(), pcuStmtBegin.getColumn());
        s.extendEnd(token.getLine(), token.getColumn() + token.getLength());
        
        int startpos = lineColToPosition(s.getLine(), s.getColumn());
        int endpos = lineColToPosition(s.getEndLine(), s.getEndColumn());
        
        // PkgStmtNode psn = new PkgStmtNode();
        ParentParsedNode cn = new ParentParsedNode(pcuNode);
        beginNode(startpos);
        pcuNode.insertNode(cn, startpos, endpos - startpos);
        completedNode(cn, startpos, endpos - startpos);
    }
    
    public void gotComment(LocatableToken token)
    {
        commentQueue.add(token);
    }
    
    @Override
    protected void gotConstructorDecl(LocatableToken token,
            LocatableToken hiddenToken)
    {
        LocatableToken start = pcuStmtBegin;
        String jdcomment = null;
        if (hiddenToken != null) {
            start = hiddenToken;
            // TODO: make certain hidden token not already consumed by prior sibling node
            jdcomment = hiddenToken.getText();
        }
        
        MethodNode pnode = new MethodNode(scopeStack.peek(), token.getText(), jdcomment);
        pnode.setModifiers(currentModifiers);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(start.getLine(), start.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void gotMethodDeclaration(LocatableToken token,
            LocatableToken hiddenToken)
    {
        LocatableToken start = pcuStmtBegin;
        String jdcomment = null;
        if (hiddenToken != null) {
            start = hiddenToken;
            // TODO: make certain hidden token not already consumed by prior sibling node
            jdcomment = hiddenToken.getText();
        }
        
        JavaEntity rtype = ParseUtils.getTypeEntity(scopeStack.peek(),
                currentQuerySource(), lastTypeSpec);
        MethodNode pnode = new MethodNode(scopeStack.peek(), token.getText(), rtype, jdcomment);
        pnode.setModifiers(currentModifiers);
        
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(start.getLine(), start.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void gotMethodParameter(LocatableToken token)
    {
        JavaEntity paramType = ParseUtils.getTypeEntity(scopeStack.peek(),
                currentQuerySource(), lastTypeSpec);
        if (paramType == null) {
            return;
        }
        while (arrayDecls-- > 0) {
            paramType = new UnresolvedArray(paramType);
        }
        MethodNode mNode = (MethodNode) scopeStack.peek();
        mNode.addParameter(token.getText(), paramType);
    }
    
    @Override
    protected void endMethodDecl(LocatableToken token, boolean included)
    {
        MethodNode mNode = (MethodNode) scopeStack.peek();
        endTopNode(token, included);
        TypeInnerNode topNode = (TypeInnerNode) scopeStack.peek();
        topNode.methodAdded(mNode);
    }
    
    @Override
    protected void beginMethodBody(LocatableToken token)
    {
        ParsedNode pnode = new ParentParsedNode(scopeStack.peek());
        pnode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void endMethodBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false);
    }
    
    @Override
    protected void gotTypeSpec(List<LocatableToken> tokens)
    {
        if (gotExtends) {
            JavaEntity supert = ParseUtils.getTypeEntity(scopeStack.peek(),
                    currentQuerySource(), tokens);
            if (supert != null) {
                extendedTypes.add(supert);
            }
        }
        else if (gotImplements) {
            JavaEntity supert = ParseUtils.getTypeEntity(scopeStack.peek(),
                    currentQuerySource(), tokens);
            if (supert != null) {
                implementedTypes.add(supert);
            }
        }
        else {
            lastTypeSpec = tokens;
            arrayDecls = 0;
        }
    }
    
    @Override
    protected void gotArrayDeclarator()
    {
        arrayDecls++;
    }
        
    @Override
    protected void beginFieldDeclarations(LocatableToken first)
    {
        arrayDecls = 0;
    }
    
    @Override
    protected void gotField(LocatableToken first, LocatableToken idToken)
    {
        JavaEntity fieldType = ParseUtils.getTypeEntity(scopeStack.peek(),
                currentQuerySource(), lastTypeSpec);
        
        lastField = new FieldNode(scopeStack.peek(), idToken.getText(), fieldType, arrayDecls);
        arrayDecls = 0;
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(first.getLine(), first.getEndColumn());
        beginNode(insPos);
        
        if (fieldType != null) {
            ParentParsedNode top = (ParentParsedNode) scopeStack.peek();
            top.insertField(lastField, insPos - curOffset, 0);
        }
        else {
            scopeStack.peek().insertNode(lastField, insPos - curOffset, 0);
        }
        
        scopeStack.push(lastField);
    }
    
    @Override
    protected void gotSubsequentField(LocatableToken first,
            LocatableToken idToken)
    {
        FieldNode field = new FieldNode(scopeStack.peek(), idToken.getText(), lastField, arrayDecls);
        arrayDecls = 0;
        int curOffset = getTopNodeOffset();
        int insPos = lineColToPosition(first.getLine(), first.getEndColumn());
        beginNode(insPos);
        
        if (lastField.getFieldType() != null) {
            ParentParsedNode top = (ParentParsedNode) scopeStack.peek();
            top.insertField(field, insPos - curOffset, 0);
        }
        else {
            scopeStack.peek().insertNode(field, insPos - curOffset, 0);
        }
        
        scopeStack.push(field);
    }
    
    @Override
    protected void endField(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    // Variables can be treated exactly like fields:
    
    @Override
    protected void beginVariableDecl(LocatableToken first)
    {
        beginFieldDeclarations(first);
    }
    
    @Override
    protected void gotVariableDecl(LocatableToken first, LocatableToken idToken, boolean inited)
    {
        gotField(first, idToken);
    }
    
    @Override
    protected void gotSubsequentVar(LocatableToken first, LocatableToken idToken, boolean inited)
    {
        gotSubsequentField(first, idToken);
    }
    
    @Override
    protected void endVariable(LocatableToken token, boolean included)
    {
        endField(token, included);
    }
    
    // For-initializers are like variables/fields
    
    @Override
    protected void beginForInitDecl(LocatableToken first)
    {
        beginFieldDeclarations(first);
    }
    
    @Override
    protected void gotForInit(LocatableToken first, LocatableToken idToken)
    {
        gotField(first, idToken);
    }
    
    @Override
    protected void gotSubsequentForInit(LocatableToken first,
            LocatableToken idToken)
    {
        gotSubsequentField(first, idToken);
    }
    
    @Override
    protected void endForInit(LocatableToken token, boolean included)
    {
        endField(token, included);
    }
    
    @Override
    protected void beginAnonClassBody(LocatableToken token)
    {
        ParsedTypeNode pnode = new ParsedTypeNode(scopeStack.peek(), null, null); // TODO generate Abc$1 ?
        int curOffset = getTopNodeOffset();
        LocatableToken begin = token;
        int insPos = lineColToPosition(begin.getLine(), begin.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
        
        TypeInnerNode bodyNode = new TypeInnerNode(scopeStack.peek());
        bodyNode.setInner(true);
        curOffset = getTopNodeOffset();
        insPos = lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        pnode.insertInner(bodyNode, insPos - curOffset, 0);
        scopeStack.push(bodyNode);
    }
    
    @Override
    protected void endAnonClassBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false);  // inner node
        endTopNode(token, included);  // outer node
    }
    
    @Override
    protected void beginExpression(LocatableToken token)
    {
        ExpressionNode nnode = new ExpressionNode(scopeStack.peek());
        int curOffset = getTopNodeOffset();
        LocatableToken begin = token;
        int insPos = lineColToPosition(begin.getLine(), begin.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(nnode, insPos - curOffset, 0);
        scopeStack.push(nnode);
    }
    
    @Override
    protected void endExpression(LocatableToken token)
    {
        endTopNode(token, false);
        arrayDecls = 0;
    }

    @Override
    protected void gotTypeDefExtends(LocatableToken extendsToken)
    {
        gotExtends = true;
        gotImplements = false;
    }
    
    @Override
    protected void gotTypeDefImplements(LocatableToken implementsToken)
    {
        gotImplements = true;
        gotExtends = false;
    }
    
}
