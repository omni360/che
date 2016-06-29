/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.java.client.editor;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.editor.annotation.AnnotationModel;
import org.eclipse.che.ide.api.editor.document.Document;
import org.eclipse.che.ide.api.editor.texteditor.TextEditorPresenter;
import org.eclipse.che.ide.api.project.node.HasProjectConfig;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.ext.java.client.JavaLocalizationConstant;
import org.eclipse.che.ide.ext.java.shared.dto.HighlightedPosition;
import org.eclipse.che.ide.ext.java.shared.dto.ReconcileResult;
import org.eclipse.che.ide.project.ResolvingProjectStateHolder;
import org.eclipse.che.ide.project.ResolvingProjectStateHolderRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.eclipse.che.ide.project.ResolvingProjectStateHolder.ResolvingProjectState.IN_PROGRESS;
import static org.eclipse.che.ide.project.ResolvingProjectStateHolder.ResolvingProjectState.RESOLVED;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Roman Nikitenko
 */
@RunWith(MockitoJUnitRunner.class)
public class JavaReconcilerStrategyTest {
    private static final String FILE_NAME = "TestClass.java";

    @Mock
    private EventBus                            eventBus;
    @Mock
    private TextEditorPresenter<?>              editor;
    @Mock
    private JavaCodeAssistProcessor             codeAssistProcessor;
    @Mock
    private AnnotationModel                     annotationModel;
    @Mock
    private HandlerRegistration                 handlerRegistration;
    @Mock
    private SemanticHighlightRenderer           highlighter;
    @Mock
    private JavaReconcileClient                 client;
    @Mock
    private ReconcileResult                     reconcileResult;
    @Mock
    private VirtualFile                         file;
    @Mock
    private ResolvingProjectStateHolder         resolvingProjectStateHolder;
    @Mock
    private ResolvingProjectStateHolderRegistry resolvingProjectStateHolderRegistry;
    @Mock
    private JavaLocalizationConstant            localizationConstant;

    @Captor
    private ArgumentCaptor<JavaReconcileClient.ReconcileCallback> reconcileCallbackCaptor;


    @InjectMocks
    private JavaReconcilerStrategy javaReconcilerStrategy;

    @Before
    public void setUp() throws Exception {
        EditorInput editorInput = mock(EditorInput.class);
        HasProjectConfig hasProjectConfig = mock(HasProjectConfig.class);
        ProjectConfigDto projectConfigDto = mock(ProjectConfigDto.class);

        when(editor.getEditorInput()).thenReturn(editorInput);
        when(editorInput.getFile()).thenReturn(file);
        when(file.getName()).thenReturn(FILE_NAME);
        when(file.getProject()).thenReturn(hasProjectConfig);
        when(hasProjectConfig.getProjectConfig()).thenReturn(projectConfigDto);
        when(resolvingProjectStateHolderRegistry.getResolvingProjectStateHolder(anyString())).thenReturn(resolvingProjectStateHolder);
        when(localizationConstant.codeAssistErrorMessageResolvingProject()).thenReturn("error");

        javaReconcilerStrategy.setDocument(mock(Document.class));
    }

    @Test
    public void shouldDisableReconcilerWhenResolvingProjectIsInProgress() throws Exception {
        when(resolvingProjectStateHolder.getState()).thenReturn(IN_PROGRESS);

        javaReconcilerStrategy.parse();

        verify(client).reconcile(anyString(), anyString(), reconcileCallbackCaptor.capture());
        JavaReconcileClient.ReconcileCallback reconcileCallback = reconcileCallbackCaptor.getValue();
        reconcileCallback.onReconcile(reconcileResult);

        verify(reconcileResult, never()).getProblems();
        verify(reconcileResult, never()).getHighlightedPositions();
        verify(codeAssistProcessor, never()).enableCodeAssistant();
        verify(codeAssistProcessor).disableCodeAssistant(anyString());
        verify(highlighter).reconcile(eq(Collections.<HighlightedPosition>emptyList()));
    }

    @Test
    public void shouldDoParseWhenResolvingProjectHasResolved() throws Exception {
        when(resolvingProjectStateHolder.getState()).thenReturn(RESOLVED);
        HighlightedPosition highlightedPosition = mock(HighlightedPosition.class);
        List<HighlightedPosition> positions = new ArrayList<>();
        positions.add(highlightedPosition);
        when(reconcileResult.getHighlightedPositions()).thenReturn(positions);

        javaReconcilerStrategy.parse();

        verify(client).reconcile(anyString(), anyString(), reconcileCallbackCaptor.capture());
        JavaReconcileClient.ReconcileCallback reconcileCallback = reconcileCallbackCaptor.getValue();
        reconcileCallback.onReconcile(reconcileResult);

        verify(reconcileResult).getProblems();
        verify(reconcileResult).getHighlightedPositions();
        verify(codeAssistProcessor).enableCodeAssistant();
        verify(codeAssistProcessor, never()).disableCodeAssistant(anyString());
        verify(highlighter).reconcile(eq(positions));
    }
}
